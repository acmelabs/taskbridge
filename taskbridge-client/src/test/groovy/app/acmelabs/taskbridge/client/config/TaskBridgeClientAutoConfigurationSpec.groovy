package app.acmelabs.taskbridge.client.config

import app.acmelabs.taskbridge.client.client.FlowableRestClient
import app.acmelabs.taskbridge.client.worker.ExternalWorkerBeanPostProcessor
import app.acmelabs.taskbridge.client.worker.TaskBridgeLifecycle
import app.acmelabs.taskbridge.client.worker.WorkerEndpointRegistry
import app.acmelabs.taskbridge.client.worker.WorkerMethodInvoker
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import spock.lang.Specification

class TaskBridgeClientAutoConfigurationSpec extends Specification {

    TaskBridgeClientAutoConfiguration config = new TaskBridgeClientAutoConfiguration()

    TaskBridgeClientProperties properties = new TaskBridgeClientProperties().tap {
        flowableBaseUrl = "http://localhost:8080"
        workerId = "test-worker"
    }
    ObjectMapper objectMapper = new ObjectMapper()

    // =========================================================================
    // Bean creation
    // =========================================================================

    def "creates WorkerEndpointRegistry"() {
        when:
        def registry = config.workerEndpointRegistry()

        then:
        registry instanceof WorkerEndpointRegistry
    }

    def "creates WorkerMethodInvoker"() {
        when:
        def invoker = config.workerMethodInvoker(objectMapper)

        then:
        invoker instanceof WorkerMethodInvoker
    }

    def "creates ExternalWorkerBeanPostProcessor"() {
        given:
        def propsProvider = Stub(ObjectProvider) { getIfAvailable() >> properties }
        def registryProvider = Stub(ObjectProvider) { getIfAvailable() >> new WorkerEndpointRegistry() }

        when:
        def processor = config.externalWorkerBeanPostProcessor(propsProvider, registryProvider)

        then:
        processor instanceof ExternalWorkerBeanPostProcessor
    }

    def "creates FlowableRestClient"() {
        when:
        def client = config.flowableRestClient(properties, objectMapper)

        then:
        client instanceof FlowableRestClient
    }

    def "creates RedisMessageListenerContainer"() {
        given:
        def connectionFactory = Mock(RedisConnectionFactory)

        when:
        def container = config.taskBridgeRedisListenerContainer(connectionFactory)

        then:
        container instanceof RedisMessageListenerContainer
    }

    def "creates TaskBridgeLifecycle"() {
        given:
        def registry = config.workerEndpointRegistry()
        def invoker = config.workerMethodInvoker(objectMapper)
        def client = config.flowableRestClient(properties, objectMapper)
        def containerProvider = Stub(ObjectProvider) { getIfAvailable() >> null }

        when:
        def lifecycle = config.taskBridgeLifecycle(registry, client, invoker, properties, containerProvider)

        then:
        lifecycle instanceof TaskBridgeLifecycle
    }

    // =========================================================================
    // Auth configuration
    // =========================================================================

    def "FlowableRestClient is created without error when username is blank"() {
        given:
        def propsNoAuth = new TaskBridgeClientProperties().tap {
            flowableBaseUrl = "http://localhost:8080"
            username = ""
        }

        when:
        def client = config.flowableRestClient(propsNoAuth, objectMapper)

        then:
        client instanceof FlowableRestClient
        noExceptionThrown()
    }

    def "FlowableRestClient is created without error when username is set"() {
        given:
        def propsWithAuth = new TaskBridgeClientProperties().tap {
            flowableBaseUrl = "http://localhost:8080"
            username = "admin"
            password = "secret"
        }

        when:
        def client = config.flowableRestClient(propsWithAuth, objectMapper)

        then:
        client instanceof FlowableRestClient
        noExceptionThrown()
    }
}
