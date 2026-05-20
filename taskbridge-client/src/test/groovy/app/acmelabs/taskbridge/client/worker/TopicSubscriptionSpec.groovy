package app.acmelabs.taskbridge.client.worker

import app.acmelabs.taskbridge.client.client.FlowableRestClient
import app.acmelabs.taskbridge.client.dto.AcquiredJob
import app.acmelabs.taskbridge.client.result.WorkerBpmnError
import app.acmelabs.taskbridge.client.result.WorkerFailure
import app.acmelabs.taskbridge.client.result.WorkerSuccess
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Timeout(10)
class TopicSubscriptionSpec extends Specification {

    FlowableRestClient flowableClient = Mock()
    WorkerMethodInvoker invoker = Mock()

    WorkerEndpoint endpoint
    TopicSubscription subscription

    def setup() {
        endpoint = endpointWithConcurrency(4)
    }

    def cleanup() {
        subscription?.stop()
    }

    // =========================================================================
    // Startup acquire
    // =========================================================================

    def "acquires jobs immediately on start without any wakeUp()"() {
        given:
        def latch = new CountDownLatch(1)
        flowableClient.acquireJobs(*_) >> { latch.countDown(); [] }
        subscription = build()

        when:
        subscription.start()

        then:
        latch.await(2, TimeUnit.SECONDS)
    }

    // =========================================================================
    // Wakeup signal
    // =========================================================================

    def "acquires again after wakeUp()"() {
        given:
        def callCount = new AtomicInteger(0)
        def secondCallLatch = new CountDownLatch(1)
        flowableClient.acquireJobs(*_) >> {
            if (callCount.incrementAndGet() == 2) secondCallLatch.countDown()
            []
        }
        subscription = build()
        subscription.start()
        sleep(50) // let initial acquire complete

        when:
        subscription.wakeUp()

        then:
        secondCallLatch.await(2, TimeUnit.SECONDS)
        callCount.get() >= 2
    }

    // =========================================================================
    // Job result dispatching
    // =========================================================================

    def "calls completeJob on WorkerSuccess"() {
        given:
        def job = job("job-1", 3)
        def vars = [result: "ok"]
        def captured = new LinkedBlockingQueue()

        flowableClient.acquireJobs(*_) >>> [[job], []]
        invoker.invoke(*_) >> new WorkerSuccess(vars)
        flowableClient.completeJob(_, _, _) >> { String id, String wid, Map v -> captured.offer([id: id, vars: v]) }

        subscription = build()

        when:
        subscription.start()
        def call = captured.poll(2, TimeUnit.SECONDS)

        then:
        call != null
        call.id == "job-1"
        call.vars == vars
    }

    def "calls failJob on WorkerFailure with explicit retries"() {
        given:
        def job = job("job-2", 3)
        def captured = new LinkedBlockingQueue()

        flowableClient.acquireJobs(*_) >>> [[job], []]
        invoker.invoke(*_) >> new WorkerFailure("bad", "details", 2)
        flowableClient.failJob(_, _, _, _, _) >> { String id, String wid, String msg, String det, int r ->
            captured.offer([id: id, msg: msg, det: det, retries: r])
        }

        subscription = build()

        when:
        subscription.start()
        def call = captured.poll(2, TimeUnit.SECONDS)

        then:
        call != null
        call.id == "job-2"
        call.msg == "bad"
        call.det == "details"
        call.retries == 2
    }

    def "calls failJob on WorkerFailure with retries=-1 decrements job retries"() {
        given:
        def job = job("job-3", 3)
        def captured = new LinkedBlockingQueue()

        flowableClient.acquireJobs(*_) >>> [[job], []]
        invoker.invoke(*_) >> new WorkerFailure("err", null, -1)
        flowableClient.failJob(_, _, _, _, _) >> { String id, String wid, String msg, String det, int r ->
            captured.offer([id: id, retries: r])
        }

        subscription = build()

        when:
        subscription.start()
        def call = captured.poll(2, TimeUnit.SECONDS)

        then:
        call != null
        call.id == "job-3"
        call.retries == 2  // 3-1=2
    }

    def "calls bpmnError on WorkerBpmnError"() {
        given:
        def job = job("job-4", 3)
        def captured = new LinkedBlockingQueue()

        flowableClient.acquireJobs(*_) >>> [[job], []]
        invoker.invoke(*_) >> new WorkerBpmnError("PAYMENT_FAILED")
        flowableClient.bpmnError(_, _, _) >> { String id, String wid, String code ->
            captured.offer([id: id, code: code])
        }

        subscription = build()

        when:
        subscription.start()
        def call = captured.poll(2, TimeUnit.SECONDS)

        then:
        call != null
        call.id == "job-4"
        call.code == "PAYMENT_FAILED"
    }

    // =========================================================================
    // Invoker exception handling
    // =========================================================================

    def "calls failJob when invoker throws, decrementing retries"() {
        given:
        def job = job("job-5", 3)
        def captured = new LinkedBlockingQueue()

        flowableClient.acquireJobs(*_) >>> [[job], []]
        invoker.invoke(*_) >> { throw new RuntimeException("worker blew up") }
        flowableClient.failJob(_, _, _, _, _) >> { String id, String wid, String msg, String det, int r ->
            captured.offer([id: id, msg: msg, retries: r])
        }

        subscription = build()

        when:
        subscription.start()
        def call = captured.poll(2, TimeUnit.SECONDS)

        then:
        call != null
        call.id == "job-5"
        call.msg == "worker blew up"
        call.retries == 2  // 3-1=2
    }

    def "retries floor at zero when job has no retries left"() {
        given:
        def job = job("job-6", 0)
        def captured = new LinkedBlockingQueue()

        flowableClient.acquireJobs(*_) >>> [[job], []]
        invoker.invoke(*_) >> { throw new RuntimeException("err") }
        flowableClient.failJob(_, _, _, _, _) >> { String id, String wid, String msg, String det, int r ->
            captured.offer([retries: r])
        }

        subscription = build()

        when:
        subscription.start()
        def call = captured.poll(2, TimeUnit.SECONDS)

        then:
        call != null
        call.retries == 0
    }

    // =========================================================================
    // Lifecycle failures (acquire errors)
    // =========================================================================

    def "loop continues after acquire throws"() {
        given:
        def callCount = new AtomicInteger(0)
        def successLatch = new CountDownLatch(1)
        flowableClient.acquireJobs(*_) >> {
            int n = callCount.incrementAndGet()
            if (n == 1) throw new RuntimeException("network error")
            if (n == 2) successLatch.countDown()
            []
        }
        subscription = new TopicSubscription(endpoint, "worker-1", flowableClient, invoker,
                50L, 0L, 10L, 50L)

        when:
        subscription.start()

        then:
        successLatch.await(3, TimeUnit.SECONDS)
        callCount.get() >= 2
    }

    // =========================================================================
    // Fallback polling
    // =========================================================================

    def "falls back to poll after fallbackPollIntervalMs without wakeUp"() {
        given:
        def callCount = new AtomicInteger(0)
        def secondLatch = new CountDownLatch(2)
        flowableClient.acquireJobs(*_) >> {
            callCount.incrementAndGet()
            secondLatch.countDown()
            []
        }
        subscription = new TopicSubscription(endpoint, "worker-1", flowableClient, invoker,
                50L, 0L, 1000L, 5000L)

        when:
        subscription.start()

        then:
        secondLatch.await(3, TimeUnit.SECONDS)
        callCount.get() >= 2
    }

    // =========================================================================
    // Re-acquire when batch is full
    // =========================================================================

    def "immediately re-acquires when batch size equals maxJobs"() {
        given:
        def callCount = new AtomicInteger(0)
        def secondCallLatch = new CountDownLatch(1)

        def fullBatch = (1..4).collect { job("job-$it", 3) }
        invoker.invoke(*_) >> new WorkerSuccess()
        flowableClient.completeJob(*_) >> null
        flowableClient.acquireJobs(*_) >> {
            int n = callCount.incrementAndGet()
            if (n == 2) secondCallLatch.countDown()
            n == 1 ? fullBatch : []
        }

        // Very long fallback — second call must come from batch-full signal, not timeout
        subscription = new TopicSubscription(endpoint, "worker-1", flowableClient, invoker,
                10_000L, 0L, 1000L, 5000L)

        when:
        subscription.start()

        then:
        secondCallLatch.await(2, TimeUnit.SECONDS)
        callCount.get() >= 2
    }

    // =========================================================================
    // Concurrency limiting
    // =========================================================================

    def "concurrencySemaphore limits parallel job execution"() {
        given:
        int maxConcurrency = 2
        def ep = endpointWithConcurrency(maxConcurrency)

        def runningCount = new AtomicInteger(0)
        def maxObserved = new AtomicInteger(0)
        def jobsDone = new CountDownLatch(4)
        def proceedLatch = new CountDownLatch(1)

        def jobs = (1..4).collect { job("job-$it", 3) }
        flowableClient.acquireJobs(*_) >>> [jobs, []]
        flowableClient.completeJob(*_) >> null

        invoker.invoke(*_) >> {
            int cur = runningCount.incrementAndGet()
            maxObserved.accumulateAndGet(cur, Math::max)
            proceedLatch.await(1, TimeUnit.SECONDS)
            runningCount.decrementAndGet()
            jobsDone.countDown()
            new WorkerSuccess()
        }

        subscription = new TopicSubscription(ep, "worker-1", flowableClient, invoker,
                50L, 0L, 1000L, 5000L)

        when:
        subscription.start()
        sleep(100)
        proceedLatch.countDown()
        jobsDone.await(3, TimeUnit.SECONDS)

        then:
        maxObserved.get() <= maxConcurrency
    }

    // =========================================================================
    // Stop
    // =========================================================================

    def "stop() terminates the poll loop cleanly"() {
        given:
        def startedLatch = new CountDownLatch(1)
        flowableClient.acquireJobs(*_) >> { startedLatch.countDown(); [] }
        subscription = build()
        subscription.start()
        startedLatch.await(2, TimeUnit.SECONDS)

        when:
        subscription.stop()

        then:
        noExceptionThrown()
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private TopicSubscription build() {
        new TopicSubscription(endpoint, "worker-1", flowableClient, invoker,
                5000L, 0L, 1000L, 5000L)
    }

    private WorkerEndpoint endpointWithConcurrency(int concurrency) {
        def method = TopicSubscriptionSpec.getDeclaredMethod("dummyWorker")
        new WorkerEndpoint("test-topic", "PT5M", 4, concurrency, 3, this, method)
    }

    private static AcquiredJob job(String id, int retries) {
        new AcquiredJob().tap {
            it.id = id
            it.topicName = "test-topic"
            it.retries = retries
            it.variables = [:]
        }
    }

    void dummyWorker() {}
}
