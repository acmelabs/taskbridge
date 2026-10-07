package app.acmelabs.taskbridge.server.acquire

import org.flowable.common.engine.impl.cfg.IdGenerator
import org.flowable.engine.ProcessEngine
import org.flowable.engine.ProcessEngineConfiguration
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration
import org.flowable.job.api.AcquiredExternalWorkerJob
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs the exclusive-aware acquire against a real (in-memory) Flowable engine, because what matters is how Flowable's
 * own {@code AcquireExternalWorkerJobsCmd} behaves with the filtered candidates: the process instance lock it takes
 * for exclusive jobs must still hold, and a batch must no longer be rolled back by jobs it cannot lock.
 */
@Timeout(60)
class ExclusiveAwareAcquireEngineSpec extends Specification {

    static final String TOPIC = "topic"

    ProcessEngine engine

    def cleanup() {
        engine?.close()
    }

    // -------------------------------------------------------------------------
    // The problem, on a stock engine
    // -------------------------------------------------------------------------

    def "control: a stock engine returns nothing when a batch holds two exclusive jobs of one process instance"() {
        given:
        engine = buildEngine(false)
        start("exclusivePair")

        expect: "every batch is rolled back, so the jobs stay unlocked"
        acquire(5, "w1").isEmpty()
        acquire(5, "w2").isEmpty()
        unlocked().size() == 2
    }

    // -------------------------------------------------------------------------
    // Exclusive-aware acquire
    // -------------------------------------------------------------------------

    def "a batch takes one of a process instance's exclusive jobs and leaves its sibling"() {
        given:
        engine = buildEngine(true)
        def pi = start("exclusivePair")

        when:
        def jobs = acquire(5, "w1")

        then:
        jobs.size() == 1
        jobs[0].processInstanceId == pi
        unlocked().size() == 1
    }

    def "a sibling waiting on an in-flight job does not hold back other process instances"() {
        given:
        engine = buildEngine(true)
        def first = start("exclusivePair")
        def inFlight = acquire(1, "w1")
        def second = start("exclusivePair")
        def third = start("exclusivePair")

        when:
        def jobs = acquire(5, "w2")

        then: "one job each from the other two instances; the in-flight instance's sibling is skipped"
        inFlight*.processInstanceId == [first]
        jobs*.processInstanceId as Set == [second, third] as Set
        jobs.size() == 2
        unlocked()*.processInstanceId.count { it == first } == 1
    }

    def "the sibling is acquired once the in-flight job completes"() {
        given:
        engine = buildEngine(true)
        def pi = start("exclusivePair")
        def first = acquire(5, "w1")
        assert acquire(5, "w2").isEmpty()

        when:
        complete(first[0], "w1")
        def second = acquire(5, "w2")

        then:
        second.size() == 1
        second[0].processInstanceId == pi
        second[0].elementId != first[0].elementId
    }

    def "an expired process instance lock no longer blocks the sibling"() {
        given: "a worker took one job with a 5 minute lock and never reported back"
        engine = buildEngine(true)
        def pi = start("exclusivePair")
        def abandoned = acquire(1, "w1")
        assert acquire(5, "w2").isEmpty()

        when:
        moveClock(Duration.ofMinutes(6))
        def jobs = acquire(5, "w2")

        then:
        jobs.size() == 1
        jobs[0].processInstanceId == pi
        jobs[0].elementId != abandoned[0].elementId
    }

    def "non-exclusive jobs of one process instance are still acquired together"() {
        given:
        engine = buildEngine(true)
        def pi = start("nonExclusivePair")

        when:
        def jobs = acquire(5, "w1")

        then:
        jobs.size() == 2
        jobs.every { it.processInstanceId == pi }
    }

    def "a non-exclusive job is acquired alongside an exclusive job of its process instance"() {
        given:
        engine = buildEngine(true)
        def pi = start("mixedPair")

        when:
        def jobs = acquire(5, "w1")

        then:
        jobs*.elementId as Set == ["taskA", "freeTask"] as Set
        jobs.every { it.processInstanceId == pi }
    }

    def "siblings waiting on in-flight jobs never use up the batch, however many pile up"() {
        given: "30 instances, each with one job in flight and its sibling waiting, all ahead of a free instance"
        engine = buildEngine(true, 0)
        def blocked = (1..30).collect { start("exclusivePair") }
        def inFlight = []
        while (true) {
            def more = acquire(5, "w1")
            if (more.isEmpty()) break
            inFlight.addAll(more)
        }
        def free = start("exclusivePair")

        when: "lookahead 0: only the requested row is read"
        def jobs = acquire(1, "w2")

        then: "every blocked instance got exactly one job, and the free instance is still reached"
        inFlight*.processInstanceId as Set == blocked as Set
        inFlight.size() == 30
        jobs*.processInstanceId == [free]
    }

    def "the lookahead lets a batch fill past a process instance's second exclusive job"() {
        given: "two fresh instances, each with two exclusive jobs"
        engine = buildEngine(true, lookahead)
        def first = start("exclusivePair")
        def second = start("exclusivePair")

        when:
        def jobs = acquire(2, "w1")

        then:
        jobs*.processInstanceId == (reachesSecond ? [first, second] : [first])

        where: "lookahead 0 reads only the first instance's two jobs, and drops its second"
        lookahead | reachesSecond
        0         | false
        1         | true
    }

    def "concurrent workers never hold two exclusive jobs of one process instance, and every instance gets one"() {
        given:
        engine = buildEngine(true)
        def instances = (1..15).collect { start("exclusivePair") }
        def workers = 4
        def acquired = new ConcurrentLinkedQueue<AcquiredExternalWorkerJob>()
        def barrier = new CyclicBarrier(workers)
        def pool = Executors.newFixedThreadPool(workers)

        when: "workers race for the 30 jobs without completing any"
        (1..workers).each { n ->
            pool.submit {
                barrier.await()
                5.times { acquired.addAll(acquire(5, "w$n")) }
            }
        }
        pool.shutdown()
        assert pool.awaitTermination(30, TimeUnit.SECONDS)

        and: "anything left over is picked up by a final pass"
        while (true) {
            def more = acquire(5, "final")
            if (more.isEmpty()) break
            acquired.addAll(more)
        }

        then: "exactly one exclusive job per process instance is locked"
        def lockedPerInstance = engine.managementService.createExternalWorkerJobQuery().list()
                .findAll { it.lockOwner != null }
                .countBy { it.processInstanceId }
        lockedPerInstance.keySet() == instances as Set
        lockedPerInstance.values().every { it == 1 }
        acquired.size() == instances.size()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ProcessEngine buildEngine(boolean exclusiveAware, int lookahead = 20) {
        def config = new StandaloneInMemProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:exclusive-acquire-${UUID.randomUUID()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE)
                // The spec drives every acquire and completion; nothing runs in the background.
                .setAsyncExecutorActivate(false)
                // Acquire orders candidates by ID_, a string; padded ids keep that in creation order.
                .setIdGenerator(new SequentialIdGenerator())
        if (exclusiveAware) {
            ExclusiveAwareAcquire.install(config, lookahead)
        }
        def built = config.buildProcessEngine()
        def deployment = built.repositoryService.createDeployment()
        [exclusivePair: ["true", "true"], nonExclusivePair: ["false", "false"], mixedPair: ["true", "false"]]
                .each { key, flags -> deployment.addString("${key}.bpmn20.xml", processXml(key, flags[0], flags[1])) }
        deployment.deploy()
        built
    }

    private String start(String key) {
        engine.runtimeService.startProcessInstanceByKey(key).id
    }

    private List<AcquiredExternalWorkerJob> acquire(int numberOfJobs, String workerId) {
        engine.managementService.createExternalWorkerJobAcquireBuilder()
                .topic(TOPIC, Duration.ofMinutes(5))
                .acquireAndLock(numberOfJobs, workerId, 3)
    }

    private void complete(AcquiredExternalWorkerJob job, String workerId) {
        engine.managementService.createExternalWorkerCompletionBuilder(job.id, workerId).complete()
    }

    private List unlocked() {
        engine.managementService.createExternalWorkerJobQuery().list().findAll { it.lockOwner == null }
    }

    private void moveClock(Duration by) {
        def clock = engine.processEngineConfiguration.clock
        clock.currentTime = new Date(clock.currentTime.time + by.toMillis())
    }

    private static String processXml(String key, String firstExclusive, String secondExclusive) {
        """<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="taskbridge">
  ${pair(key, firstExclusive, secondExclusive)}
</definitions>"""
    }

    static class SequentialIdGenerator implements IdGenerator {
        private final AtomicLong next = new AtomicLong()

        @Override
        String getNextId() {
            String.format("%012d", next.incrementAndGet())
        }
    }

    /** Two external worker tasks on {@link #TOPIC}, reached together through a parallel gateway. */
    private static String pair(String key, String firstExclusive, String secondExclusive) {
        def second = secondExclusive == "false" && firstExclusive == "true" ? "freeTask" : "taskB"
        """
  <process id="$key" isExecutable="true">
    <startEvent id="start"/>
    <sequenceFlow id="toFork" sourceRef="start" targetRef="fork"/>
    <parallelGateway id="fork"/>
    <sequenceFlow id="toA" sourceRef="fork" targetRef="taskA"/>
    <sequenceFlow id="toB" sourceRef="fork" targetRef="$second"/>
    <serviceTask id="taskA" flowable:type="external-worker" flowable:topic="$TOPIC" flowable:exclusive="$firstExclusive"/>
    <serviceTask id="$second" flowable:type="external-worker" flowable:topic="$TOPIC" flowable:exclusive="$secondExclusive"/>
    <sequenceFlow id="fromA" sourceRef="taskA" targetRef="join"/>
    <sequenceFlow id="fromB" sourceRef="$second" targetRef="join"/>
    <parallelGateway id="join"/>
    <sequenceFlow id="toEnd" sourceRef="join" targetRef="end"/>
    <endEvent id="end"/>
  </process>"""
    }
}
