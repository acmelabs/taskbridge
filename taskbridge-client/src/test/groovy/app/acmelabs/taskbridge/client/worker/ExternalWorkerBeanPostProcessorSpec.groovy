package app.acmelabs.taskbridge.client.worker

import app.acmelabs.taskbridge.client.config.TaskBridgeClientProperties
import spock.lang.Specification

class ExternalWorkerBeanPostProcessorSpec extends Specification {

    WorkerEndpointRegistry registry = Mock()
    TaskBridgeClientProperties properties = new TaskBridgeClientProperties().tap {
        lockDuration = "PT10M"
        maxJobs = 10
        concurrency = 5
    }
    ExternalWorkerBeanPostProcessor processor = new ExternalWorkerBeanPostProcessor(properties, registry)

    // =========================================================================
    // Discovery
    // =========================================================================

    def "discovers @ExternalWorker method and registers one endpoint with the bean"() {
        given:
        def bean = new TestWorkerBeans.SingleWorkerBean()

        when:
        processor.postProcessAfterInitialization(bean, "bean")
        processor.afterSingletonsInstantiated()

        then:
        1 * registry.register({ WorkerEndpoint ep ->
            ep.topic == "payment-task" && ep.bean.is(bean)
        })
    }

    def "skips beans with no @ExternalWorker methods"() {
        given:
        def bean = new TestWorkerBeans.PlainBean()

        when:
        processor.postProcessAfterInitialization(bean, "bean")
        processor.afterSingletonsInstantiated()

        then:
        0 * registry.register(_)
    }

    def "registers two endpoints for a method with two @ExternalWorker annotations"() {
        given:
        def bean = new TestWorkerBeans.MultiTopicBean()

        when:
        processor.postProcessAfterInitialization(bean, "bean")
        processor.afterSingletonsInstantiated()

        then:
        1 * registry.register({ WorkerEndpoint ep -> ep.topic == "topic-a" })
        1 * registry.register({ WorkerEndpoint ep -> ep.topic == "topic-b" })
    }

    // =========================================================================
    // Default resolution
    // =========================================================================

    def "uses annotation values when explicitly set"() {
        given:
        def bean = new TestWorkerBeans.ExplicitValuesBean()

        when:
        processor.postProcessAfterInitialization(bean, "bean")
        processor.afterSingletonsInstantiated()

        then:
        1 * registry.register({ WorkerEndpoint ep ->
            ep.topic == "explicit-topic" &&
            ep.lockDuration == "PT1M" &&
            ep.maxJobs == 2 &&
            ep.concurrency == 1
        })
    }

    def "falls back to global properties when annotation uses defaults"() {
        given:
        def bean = new TestWorkerBeans.DefaultValuesBean()

        when:
        processor.postProcessAfterInitialization(bean, "bean")
        processor.afterSingletonsInstantiated()

        then:
        1 * registry.register({ WorkerEndpoint ep ->
            ep.lockDuration == "PT10M" &&
            ep.maxJobs == 10 &&
            ep.concurrency == 5
        })
    }

    // =========================================================================
    // Supported parameter / return types
    // =========================================================================

    def "accepts all three supported parameter types on one method"() {
        given:
        def bean = new TestWorkerBeans.AllParamTypesBean()

        when:
        processor.postProcessAfterInitialization(bean, "bean")
        processor.afterSingletonsInstantiated()

        then:
        1 * registry.register({ WorkerEndpoint ep -> ep.topic == "all-params" })
    }

    def "accepts ExternalWorkerResult return type"() {
        given:
        def bean = new TestWorkerBeans.ReturnsResultBean()

        when:
        processor.postProcessAfterInitialization(bean, "bean")
        processor.afterSingletonsInstantiated()

        then:
        1 * registry.register({ WorkerEndpoint ep -> ep.topic == "returns-result" })
    }

    def "accepts Map return type"() {
        given:
        def bean = new TestWorkerBeans.ReturnsMapBean()

        when:
        processor.postProcessAfterInitialization(bean, "bean")
        processor.afterSingletonsInstantiated()

        then:
        1 * registry.register({ WorkerEndpoint ep -> ep.topic == "returns-map" })
    }

    // =========================================================================
    // Validation failures
    // =========================================================================

    def "throws when @ExternalWorker method is not public"() {
        given:
        def bean = new TestWorkerBeans.PrivateWorkerBean()

        when:
        processor.postProcessAfterInitialization(bean, "bean")

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("must be public")
    }

    def "throws when @ExternalWorker method has an unsupported parameter type"() {
        given:
        def bean = new TestWorkerBeans.BadParamBean()

        when:
        processor.postProcessAfterInitialization(bean, "bean")

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Unsupported parameter type")
        ex.message.contains("java.lang.String")
    }

    def "throws when @ExternalWorker topic is blank"() {
        given:
        def bean = new TestWorkerBeans.BlankTopicBean()

        when:
        processor.postProcessAfterInitialization(bean, "bean")

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("topic must not be blank")
    }

    def "throws when @ExternalWorker method has an unsupported return type"() {
        given:
        def bean = new TestWorkerBeans.BadReturnBean()

        when:
        processor.postProcessAfterInitialization(bean, "bean")

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Unsupported return type")
        ex.message.contains("java.lang.String")
    }

    // =========================================================================
    // Endpoint carries the right method reference
    // =========================================================================

    def "endpoint holds the exact Method object from the bean class"() {
        given:
        def bean = new TestWorkerBeans.SingleWorkerBean()
        def expectedMethod = TestWorkerBeans.SingleWorkerBean.getDeclaredMethod("process",
                app.acmelabs.taskbridge.client.dto.AcquiredJob)

        when:
        processor.postProcessAfterInitialization(bean, "bean")
        processor.afterSingletonsInstantiated()

        then:
        1 * registry.register({ WorkerEndpoint ep -> ep.method == expectedMethod })
    }
}
