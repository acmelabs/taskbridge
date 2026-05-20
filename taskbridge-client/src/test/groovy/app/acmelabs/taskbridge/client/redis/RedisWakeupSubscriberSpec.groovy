package app.acmelabs.taskbridge.client.redis

import app.acmelabs.taskbridge.client.worker.TopicSubscription
import org.springframework.data.redis.connection.Message
import spock.lang.Specification

class RedisWakeupSubscriberSpec extends Specification {

    def prefix = "taskbridge:"

    private Message message(String channel, String body = "work_available") {
        Mock(Message) {
            getChannel() >> channel.bytes
            getBody() >> body.bytes
        }
    }

    def "wakes up matching subscription when channel matches"() {
        given:
        def sub = Mock(TopicSubscription)
        def subscriber = new RedisWakeupSubscriber(["payment-task": sub], prefix)

        when:
        subscriber.onMessage(message("taskbridge:payment-task"), null)

        then:
        1 * sub.wakeUp()
    }

    def "ignores message on unknown topic"() {
        given:
        def sub = Mock(TopicSubscription)
        def subscriber = new RedisWakeupSubscriber(["payment-task": sub], prefix)

        when:
        subscriber.onMessage(message("taskbridge:other-topic"), null)

        then:
        0 * sub.wakeUp()
    }

    def "ignores message with wrong prefix"() {
        given:
        def sub = Mock(TopicSubscription)
        def subscriber = new RedisWakeupSubscriber(["payment-task": sub], prefix)

        when:
        subscriber.onMessage(message("other:payment-task"), null)

        then:
        0 * sub.wakeUp()
    }

    def "routes to correct subscription among multiple"() {
        given:
        def subA = Mock(TopicSubscription)
        def subB = Mock(TopicSubscription)
        def subscriber = new RedisWakeupSubscriber(["topic-a": subA, "topic-b": subB], prefix)

        when:
        subscriber.onMessage(message("taskbridge:topic-b"), null)

        then:
        0 * subA.wakeUp()
        1 * subB.wakeUp()
    }

    def "handles empty subscriptions map gracefully"() {
        given:
        def subscriber = new RedisWakeupSubscriber([:], prefix)

        when:
        subscriber.onMessage(message("taskbridge:any-topic"), null)

        then:
        noExceptionThrown()
    }
}
