package app.acmelabs.taskbridge.client.worker

import app.acmelabs.taskbridge.client.client.FlowableRestClient
import app.acmelabs.taskbridge.client.config.TaskBridgeClientProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Timeout(10)
class TaskBridgeLifecycleSpec extends Specification {

    FlowableRestClient flowableClient = Mock()
    WorkerMethodInvoker invoker = new WorkerMethodInvoker(new ObjectMapper())
    WorkerEndpointRegistry registry = new WorkerEndpointRegistry()
    TaskBridgeClientProperties properties = new TaskBridgeClientProperties().tap {
        workerId = "test-worker"
        redisChannelPrefix = "taskbridge:"
        fallbackPollIntervalMs = 5000
        acquireJitterMs = 0
        retryBackoffMs = 1000
        maxRetryBackoffMs = 5000
    }

    ObjectProvider<RedisMessageListenerContainer> noRedis = Stub(ObjectProvider) {
        getIfAvailable() >> null
    }

    TaskBridgeLifecycle lifecycle

    def cleanup() {
        lifecycle?.stop()
    }

    // =========================================================================
    // Start
    // =========================================================================

    def "start() begins polling for each registered endpoint"() {
        given:
        def latch = new CountDownLatch(1)
        flowableClient.acquireJobs(*_) >> { latch.countDown(); [] }
        registry.register(makeEndpoint("payment-task"))
        lifecycle = new TaskBridgeLifecycle(registry, flowableClient, invoker, properties, noRedis)

        when:
        lifecycle.start()

        then:
        latch.await(2, TimeUnit.SECONDS)
        lifecycle.isRunning()
    }

    def "start() with empty registry sets running without polling"() {
        given:
        lifecycle = new TaskBridgeLifecycle(registry, flowableClient, invoker, properties, noRedis)

        when:
        lifecycle.start()

        then:
        lifecycle.isRunning()
        0 * flowableClient.acquireJobs(*_)
    }

    def "start() polls all registered topics"() {
        given:
        def topicsPolled = Collections.synchronizedSet(new HashSet<String>())
        def latch = new CountDownLatch(2)
        flowableClient.acquireJobs(*_) >> { String topic, String lock, int max, int retries, String wid ->
            if (topicsPolled.add(topic)) latch.countDown()
            []
        }
        registry.register(makeEndpoint("topic-a"))
        registry.register(makeEndpoint("topic-b"))
        lifecycle = new TaskBridgeLifecycle(registry, flowableClient, invoker, properties, noRedis)

        when:
        lifecycle.start()

        then:
        latch.await(2, TimeUnit.SECONDS)
        topicsPolled == ["topic-a", "topic-b"] as Set
    }

    def "start() registers wakeup listener for each topic when Redis container available"() {
        given:
        def container = Mock(RedisMessageListenerContainer)
        def redisProvider = Stub(ObjectProvider) { getIfAvailable() >> container }
        flowableClient.acquireJobs(*_) >> []
        registry.register(makeEndpoint("payment-task"))
        registry.register(makeEndpoint("fraud-check"))
        lifecycle = new TaskBridgeLifecycle(registry, flowableClient, invoker, properties, redisProvider)

        when:
        lifecycle.start()

        then:
        2 * container.addMessageListener(_, _)
    }

    def "start() continues without Redis when container is absent"() {
        given:
        def latch = new CountDownLatch(1)
        flowableClient.acquireJobs(*_) >> { latch.countDown(); [] }
        registry.register(makeEndpoint("payment-task"))
        lifecycle = new TaskBridgeLifecycle(registry, flowableClient, invoker, properties, noRedis)

        when:
        lifecycle.start()

        then:
        latch.await(2, TimeUnit.SECONDS)
        noExceptionThrown()
    }

    // =========================================================================
    // Stop
    // =========================================================================

    def "stop() stops all polling and marks lifecycle as not running"() {
        given:
        flowableClient.acquireJobs(*_) >> []
        registry.register(makeEndpoint("payment-task"))
        lifecycle = new TaskBridgeLifecycle(registry, flowableClient, invoker, properties, noRedis)
        lifecycle.start()

        when:
        lifecycle.stop()

        then:
        !lifecycle.isRunning()
    }

    def "stop() is safe when called on unstarted lifecycle"() {
        given:
        lifecycle = new TaskBridgeLifecycle(registry, flowableClient, invoker, properties, noRedis)

        when:
        lifecycle.stop()

        then:
        !lifecycle.isRunning()
        noExceptionThrown()
    }

    // =========================================================================
    // Lifecycle contract
    // =========================================================================

    def "isAutoStartup() returns true"() {
        given:
        lifecycle = new TaskBridgeLifecycle(registry, flowableClient, invoker, properties, noRedis)

        expect:
        lifecycle.isAutoStartup()
    }

    def "getPhase() returns a high value for late startup"() {
        given:
        lifecycle = new TaskBridgeLifecycle(registry, flowableClient, invoker, properties, noRedis)

        expect:
        lifecycle.getPhase() > Integer.MAX_VALUE / 2
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private WorkerEndpoint makeEndpoint(String topic) {
        def method = TaskBridgeLifecycleSpec.getDeclaredMethod("dummyWorker")
        new WorkerEndpoint(topic, "PT5M", 4, 4, 3, this, method)
    }

    void dummyWorker() {}
}
