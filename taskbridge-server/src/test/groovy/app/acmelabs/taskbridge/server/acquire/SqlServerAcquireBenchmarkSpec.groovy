package app.acmelabs.taskbridge.server.acquire

import org.apache.ibatis.datasource.pooled.PooledDataSource
import org.flowable.common.engine.impl.cfg.IdGenerator
import org.flowable.engine.ProcessEngine
import org.flowable.engine.ProcessEngineConfiguration
import org.flowable.engine.impl.cfg.StandaloneProcessEngineConfiguration
import org.testcontainers.containers.MSSQLServerContainer
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Timeout

import javax.sql.DataSource
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Before/after benchmark of the exclusive-aware acquire against SQL Server. Opt-in, because it needs Docker and takes
 * a few minutes:
 *
 * <pre>
 * mvn test -pl taskbridge-server -Dtest=SqlServerAcquireBenchmarkSpec -Dtaskbridge.benchmark=true
 * </pre>
 *
 * <p>Every variant gets a fresh database with {@code READ_COMMITTED_SNAPSHOT ON} (the Azure SQL default). "before"
 * is a stock engine, "after" has {@link ExclusiveAwareAcquire} installed. A proxy data source counts the JDBC
 * statements and rollbacks each acquire costs. The report is printed at the end of the run.
 *
 * <p>Read the columns against each other rather than the absolute times: on an ARM machine the SQL Server image runs
 * under emulation. Only the "after" variants assert anything, and only outcomes, never timings: the "before"
 * variants document stock Flowable's behaviour.
 *
 * <p>Re-run this after a Flowable upgrade, together with the control test in {@link ExclusiveAwareAcquireEngineSpec}.
 */
@Requires({ sys['taskbridge.benchmark'] == 'true' })
@Stepwise
@Timeout(value = 20, unit = TimeUnit.MINUTES)
class SqlServerAcquireBenchmarkSpec extends Specification {

    static final String TOPIC = "topic"
    static final int LOOKAHEAD = 20

    @Shared MSSQLServerContainer sqlServer
    @Shared List<String> report = []

    def setupSpec() {
        sqlServer = new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense()
        sqlServer.start()
    }

    def cleanupSpec() {
        sqlServer?.stop()
        println "\n=========================== EXCLUSIVE ACQUIRE BENCHMARK (SQL Server) ==========================="
        report.each { println it }
        println "================================================================================================"
    }

    // -------------------------------------------------------------------------
    // Scenarios
    // -------------------------------------------------------------------------

    def "normal acquire: 300 exclusive jobs with 3,000 jobs of another topic in the table"() {
        expect:
        [false, true].each { exclusiveAware ->
            def env = newEngine("normal", exclusiveAware)
            300.times {
                10.times { start(env, "noise") }
                start(env, "single")
            }
            def m = measureCalls(env, 50, 5)
            row("normal acquire, +3,000 noise jobs", exclusiveAware, m)
            if (exclusiveAware) assert m.jobs == 250
            close(env)
        }
    }

    def "two exclusive jobs per process instance, nothing completed"() {
        expect:
        [false, true].each { exclusiveAware ->
            def env = newEngine("pairs", exclusiveAware)
            100.times { start(env, "pair") }
            def m = measureCalls(env, 30, 5)
            row("100 instances x 2 exclusive jobs", exclusiveAware, m)
            if (exclusiveAware) assert m.jobs == 100
            close(env)
        }
    }

    def "a sibling waiting on a running job, ahead of 200 free jobs"() {
        expect:
        [false, true].each { exclusiveAware ->
            def env = newEngine("blocked", exclusiveAware)
            start(env, "pair")
            assert acquire(env.engine as ProcessEngine, 1, "in-flight").size() == 1
            200.times { start(env, "single") }
            def m = measureCalls(env, 30, 5)
            row("1 blocked sibling ahead of 200 jobs", exclusiveAware, m)
            if (exclusiveAware) assert m.jobs == 150
            close(env)
        }
    }

    def "4 concurrent workers acquire and complete 100 instances x 2 exclusive jobs"() {
        expect:
        [[false, 5], [false, 1], [true, 5]].each { variant ->
            boolean exclusiveAware = variant[0]
            int maxJobs = variant[1]
            def result = runWorkers(exclusiveAware, maxJobs, 4, 100, Duration.ofSeconds(60))
            report << String.format("%-38s %-24s wall=%6.1fs jobsDone=%3d/%d acquireCalls=%5d emptyCalls=%5d stmts=%6d rollbacks=%5d%s",
                    "4 workers, acquire + complete", variantName(exclusiveAware) + " maxJobs=$maxJobs",
                    result.seconds, result.done, result.total, result.calls, result.empty, result.stmts, result.rollbacks,
                    result.done < result.total ? " (hit time limit)" : "")
            if (exclusiveAware) assert result.done == result.total
        }
    }

    // -------------------------------------------------------------------------
    // Measurement
    // -------------------------------------------------------------------------

    private Map measureCalls(Map env, int calls, int maxJobs) {
        def engine = env.engine as ProcessEngine
        def counters = env.counters as Counters
        // Warm the pool and the code paths on a topic with no jobs.
        5.times {
            engine.managementService.createExternalWorkerJobAcquireBuilder()
                    .topic("warm-up", Duration.ofMinutes(5)).acquireAndLock(maxJobs, "warm-up", 3)
        }

        List<Double> latencies = []
        long statements = 0, rollbacks = 0
        int jobs = 0, empty = 0
        calls.times {
            counters.reset()
            long started = System.nanoTime()
            def acquired = acquire(engine, maxJobs, "worker")
            latencies << (System.nanoTime() - started) / 1e6
            statements += counters.statements.get()
            rollbacks += counters.rollbacks.get()
            jobs += acquired.size()
            if (acquired.isEmpty()) empty++
        }
        latencies.sort()
        [calls     : calls,
         p50       : latencies[(int) (latencies.size() * 0.5)],
         p95       : latencies[Math.min(latencies.size() - 1, (int) (latencies.size() * 0.95))],
         statements: statements / calls,
         rollbacks : rollbacks / calls,
         jobs      : jobs,
         empty     : empty]
    }

    private Map runWorkers(boolean exclusiveAware, int maxJobs, int workers, int instances, Duration limit) {
        def env = newEngine("workers", exclusiveAware)
        def engine = env.engine as ProcessEngine
        instances.times { start(env, "pair") }
        int total = instances * 2

        def done = new AtomicInteger()
        def calls = new AtomicInteger()
        def empty = new AtomicInteger()
        def barrier = new CyclicBarrier(workers)
        def pool = Executors.newFixedThreadPool(workers)
        (env.counters as Counters).reset()
        long deadline = System.currentTimeMillis() + limit.toMillis()
        long started = System.nanoTime()

        workers.times { n ->
            pool.submit {
                barrier.await()
                while (done.get() < total && System.currentTimeMillis() < deadline) {
                    def jobs = acquire(engine, maxJobs, "worker-$n")
                    calls.incrementAndGet()
                    if (jobs.isEmpty()) {
                        empty.incrementAndGet()
                        Thread.sleep(50)
                        continue
                    }
                    jobs.each { job ->
                        engine.managementService.createExternalWorkerCompletionBuilder(job.id, "worker-$n").complete()
                        done.incrementAndGet()
                    }
                }
            }
        }
        pool.shutdown()
        pool.awaitTermination(limit.toSeconds() + 30, TimeUnit.SECONDS)

        def counters = env.counters as Counters
        def result = [seconds  : (System.nanoTime() - started) / 1e9, done: done.get(), total: total, calls: calls.get(),
                      empty    : empty.get(), stmts: counters.statements.get(), rollbacks: counters.rollbacks.get()]
        close(env)
        result
    }

    private void row(String scenario, boolean exclusiveAware, Map m) {
        report << String.format("%-38s %-24s p50=%7.1fms p95=%7.1fms stmts/call=%5.1f rollbacks/call=%4.1f jobsAcquired=%4d emptyCalls=%3d/%d",
                scenario, variantName(exclusiveAware), m.p50 as double, m.p95 as double, m.statements as double,
                m.rollbacks as double, m.jobs, m.empty, m.calls)
    }

    private static String variantName(boolean exclusiveAware) {
        exclusiveAware ? "after (exclusive-aware)" : "before (stock)"
    }

    // -------------------------------------------------------------------------
    // Engine
    // -------------------------------------------------------------------------

    private Map newEngine(String scenario, boolean exclusiveAware) {
        String database = "tb_${scenario}_${exclusiveAware ? 'after' : 'before'}_${System.nanoTime()}"
        DriverManager.getConnection(sqlServer.jdbcUrl, sqlServer.username, sqlServer.password).withCloseable { connection ->
            connection.createStatement().execute("CREATE DATABASE [$database]")
            connection.createStatement().execute("ALTER DATABASE [$database] SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE")
        }

        def pooled = new PooledDataSource("com.microsoft.sqlserver.jdbc.SQLServerDriver",
                "${sqlServer.jdbcUrl};databaseName=$database", sqlServer.username, sqlServer.password)
        pooled.poolMaximumActiveConnections = 20
        pooled.poolMaximumIdleConnections = 20
        def counters = new Counters()

        def configuration = new StandaloneProcessEngineConfiguration()
                .setDataSource(counting(pooled, counters))
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE)
                // The benchmark drives every acquire and completion; nothing runs in the background.
                .setAsyncExecutorActivate(false)
                // Acquire orders candidates by ID_, a string; padded ids keep that in creation order.
                .setIdGenerator(new SequentialIdGenerator())
        if (exclusiveAware) {
            ExclusiveAwareAcquire.install(configuration, LOOKAHEAD)
        }
        ProcessEngine engine = configuration.buildProcessEngine()
        engine.repositoryService.createDeployment()
                .addString("single.bpmn20.xml", single("single", TOPIC))
                .addString("noise.bpmn20.xml", single("noise", "noise-topic"))
                .addString("pair.bpmn20.xml", pair())
                .deploy()
        [engine: engine, counters: counters]
    }

    private static void start(Map env, String key) {
        (env.engine as ProcessEngine).runtimeService.startProcessInstanceByKey(key)
    }

    private static List acquire(ProcessEngine engine, int numberOfJobs, String workerId) {
        engine.managementService.createExternalWorkerJobAcquireBuilder()
                .topic(TOPIC, Duration.ofMinutes(5))
                .acquireAndLock(numberOfJobs, workerId, 3)
    }

    private static void close(Map env) {
        (env.engine as ProcessEngine).close()
    }

    /** Wraps a data source so every connection counts the statements it prepares and the rollbacks it runs. */
    private DataSource counting(DataSource target, Counters counters) {
        def connectionHandler = { Connection real ->
            { proxy, method, args ->
                switch (method.name) {
                    case ["prepareStatement", "createStatement", "prepareCall"]: counters.statements.incrementAndGet(); break
                    case "rollback": counters.rollbacks.incrementAndGet(); break
                }
                try {
                    method.invoke(real, args)
                } catch (InvocationTargetException e) {
                    throw e.cause
                }
            } as InvocationHandler
        }
        (DataSource) Proxy.newProxyInstance(getClass().classLoader, [DataSource] as Class[], { proxy, method, args ->
            def result
            try {
                result = method.invoke(target, args)
            } catch (InvocationTargetException e) {
                throw e.cause
            }
            method.name == "getConnection"
                    ? Proxy.newProxyInstance(getClass().classLoader, [Connection] as Class[], connectionHandler(result as Connection))
                    : result
        } as InvocationHandler)
    }

    static class Counters {
        final AtomicLong statements = new AtomicLong()
        final AtomicLong rollbacks = new AtomicLong()

        void reset() {
            statements.set(0)
            rollbacks.set(0)
        }
    }

    static class SequentialIdGenerator implements IdGenerator {
        private final AtomicLong next = new AtomicLong()

        @Override
        String getNextId() {
            String.format("%012d", next.incrementAndGet())
        }
    }

    // -------------------------------------------------------------------------
    // Models
    // -------------------------------------------------------------------------

    /** One exclusive external worker task. */
    private static String single(String key, String topic) {
        """<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="taskbridge">
  <process id="$key" isExecutable="true">
    <startEvent id="start"/>
    <sequenceFlow id="toTask" sourceRef="start" targetRef="task"/>
    <serviceTask id="task" flowable:type="external-worker" flowable:topic="$topic"/>
    <sequenceFlow id="toEnd" sourceRef="task" targetRef="end"/>
    <endEvent id="end"/>
  </process>
</definitions>"""
    }

    /** Two exclusive external worker tasks on {@link #TOPIC}, reached together through a parallel gateway. */
    private static String pair() {
        """<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="taskbridge">
  <process id="pair" isExecutable="true">
    <startEvent id="start"/>
    <sequenceFlow id="toFork" sourceRef="start" targetRef="fork"/>
    <parallelGateway id="fork"/>
    <sequenceFlow id="toA" sourceRef="fork" targetRef="taskA"/>
    <sequenceFlow id="toB" sourceRef="fork" targetRef="taskB"/>
    <serviceTask id="taskA" flowable:type="external-worker" flowable:topic="$TOPIC"/>
    <serviceTask id="taskB" flowable:type="external-worker" flowable:topic="$TOPIC"/>
    <sequenceFlow id="fromA" sourceRef="taskA" targetRef="join"/>
    <sequenceFlow id="fromB" sourceRef="taskB" targetRef="join"/>
    <parallelGateway id="join"/>
    <sequenceFlow id="toEnd" sourceRef="join" targetRef="end"/>
    <endEvent id="end"/>
  </process>
</definitions>"""
    }
}
