package app.acmelabs.taskbridge.client.redis;

import app.acmelabs.taskbridge.client.worker.TopicSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class RedisWakeupSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisWakeupSubscriber.class);

    private final Map<String, TopicSubscription> subscriptionsByTopic;
    private final String channelPrefix;

    public RedisWakeupSubscriber(Map<String, TopicSubscription> subscriptionsByTopic, String channelPrefix) {
        this.subscriptionsByTopic = subscriptionsByTopic;
        this.channelPrefix = channelPrefix;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        if (!channel.startsWith(channelPrefix)) return;

        String topic = channel.substring(channelPrefix.length());
        TopicSubscription sub = subscriptionsByTopic.get(topic);
        if (sub != null) {
            log.info("Received Redis wakeup for topic={}", topic);
            sub.wakeUp();
        }
    }
}
