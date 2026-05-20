package app.acmelabs.taskbridge.server

import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent
import org.flowable.common.engine.api.delegate.event.FlowableEvent
import org.flowable.job.service.impl.persistence.entity.ExternalWorkerJobEntity
import org.springframework.data.redis.core.StringRedisTemplate
import spock.lang.Specification

class ExternalJobCreatedListenerSpec extends Specification {

    StringRedisTemplate redisTemplate = Mock()
    ExternalJobCreatedListener listener = new ExternalJobCreatedListener(redisTemplate, "taskbridge:")

    // -------------------------------------------------------------------------
    // Filtering
    // -------------------------------------------------------------------------

    def "ignores events with type other than ENTITY_CREATED"() {
        given:
        def event = Mock(FlowableEvent)
        event.getType() >> FlowableEngineEventType.ENTITY_UPDATED

        when:
        listener.onEvent(event)

        then:
        0 * redisTemplate.convertAndSend(_, _)
    }

    def "ignores ENTITY_CREATED that is not a FlowableEntityEvent"() {
        given:
        def event = Mock(FlowableEvent)
        event.getType() >> FlowableEngineEventType.ENTITY_CREATED

        when:
        listener.onEvent(event)

        then:
        0 * redisTemplate.convertAndSend(_, _)
    }

    def "ignores FlowableEntityEvent whose entity is not an ExternalWorkerJobEntity"() {
        given:
        def event = Mock(FlowableEntityEvent)
        event.getType() >> FlowableEngineEventType.ENTITY_CREATED
        event.getEntity() >> new Object()

        when:
        listener.onEvent(event)

        then:
        0 * redisTemplate.convertAndSend(_, _)
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    def "publishes channelPrefix+topic with payload 'work_available'"() {
        given:
        def job = Mock(ExternalWorkerJobEntity)
        job.getJobHandlerConfiguration() >> "payment-processing"

        def event = Mock(FlowableEntityEvent)
        event.getType() >> FlowableEngineEventType.ENTITY_CREATED
        event.getEntity() >> job

        when:
        listener.onEvent(event)

        then:
        1 * redisTemplate.convertAndSend("taskbridge:payment-processing", "work_available")
    }

    def "respects a custom channel prefix"() {
        given:
        def customListener = new ExternalJobCreatedListener(redisTemplate, "myapp:")
        def job = Mock(ExternalWorkerJobEntity)
        job.getJobHandlerConfiguration() >> "orders"

        def event = Mock(FlowableEntityEvent)
        event.getType() >> FlowableEngineEventType.ENTITY_CREATED
        event.getEntity() >> job

        when:
        customListener.onEvent(event)

        then:
        1 * redisTemplate.convertAndSend("myapp:orders", "work_available")
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    def "swallows Redis exceptions so the Flowable transaction does not roll back"() {
        given:
        def job = Mock(ExternalWorkerJobEntity)
        job.getJobHandlerConfiguration() >> "some-topic"

        def event = Mock(FlowableEntityEvent)
        event.getType() >> FlowableEngineEventType.ENTITY_CREATED
        event.getEntity() >> job

        redisTemplate.convertAndSend(_, _) >> { throw new RuntimeException("connection refused") }

        when:
        listener.onEvent(event)

        then:
        noExceptionThrown()
    }

    // -------------------------------------------------------------------------
    // Transaction lifecycle configuration
    // -------------------------------------------------------------------------

    def "isFailOnException returns false"() {
        expect:
        !listener.isFailOnException()
    }

    def "isFireOnTransactionLifecycleEvent returns true"() {
        expect:
        listener.isFireOnTransactionLifecycleEvent()
    }

    def "getOnTransaction returns COMMITTED"() {
        expect:
        listener.getOnTransaction() == "COMMITTED"
    }
}
