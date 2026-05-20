package app.acmelabs.taskbridge.client.worker;

import app.acmelabs.taskbridge.client.client.FlowableRestClient;
import app.acmelabs.taskbridge.client.config.TaskBridgeClientProperties;
import app.acmelabs.taskbridge.client.redis.RedisWakeupSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskBridgeLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TaskBridgeLifecycle.class);

    private final WorkerEndpointRegistry registry;
    private final FlowableRestClient flowableClient;
    private final WorkerMethodInvoker invoker;
    private final TaskBridgeClientProperties properties;
    private final ObjectProvider<RedisMessageListenerContainer> listenerContainerProvider;

    private volatile boolean running = false;
    private final List<TopicSubscription> activeSubscriptions = new ArrayList<>();

    public TaskBridgeLifecycle(WorkerEndpointRegistry registry,
                               FlowableRestClient flowableClient,
                               WorkerMethodInvoker invoker,
                               TaskBridgeClientProperties properties,
                               ObjectProvider<RedisMessageListenerContainer> listenerContainerProvider) {
        this.registry = registry;
        this.flowableClient = flowableClient;
        this.invoker = invoker;
        this.properties = properties;
        this.listenerContainerProvider = listenerContainerProvider;
    }

    @Override
    public void start() {
        List<WorkerEndpoint> endpoints = registry.getEndpoints();
        if (endpoints.isEmpty()) {
            log.info("TaskBridge: no worker endpoints registered, skipping start");
            running = true;
            return;
        }

        Map<String, TopicSubscription> subsByTopic = new LinkedHashMap<>();
        for (WorkerEndpoint ep : endpoints) {
            TopicSubscription sub = new TopicSubscription(
                    ep,
                    properties.getWorkerId(),
                    flowableClient,
                    invoker,
                    properties.getFallbackPollIntervalMs(),
                    properties.getAcquireJitterMs(),
                    properties.getRetryBackoffMs(),
                    properties.getMaxRetryBackoffMs());
            subsByTopic.put(ep.getTopic(), sub);
            activeSubscriptions.add(sub);
        }

        RedisMessageListenerContainer container = listenerContainerProvider.getIfAvailable();
        if (container != null) {
            RedisWakeupSubscriber wakeupSubscriber = new RedisWakeupSubscriber(subsByTopic, properties.getRedisChannelPrefix());
            for (String topic : subsByTopic.keySet()) {
                container.addMessageListener(wakeupSubscriber,
                        new ChannelTopic(properties.getRedisChannelPrefix() + topic));
            }
        } else {
            log.warn("TaskBridge: no RedisMessageListenerContainer available — wakeup signals disabled");
        }

        for (TopicSubscription sub : activeSubscriptions) {
            sub.start();
        }

        log.info("TaskBridge: started {} subscription(s): {}", activeSubscriptions.size(), subsByTopic.keySet());
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        for (TopicSubscription sub : activeSubscriptions) {
            sub.stop();
        }
        activeSubscriptions.clear();
        log.info("TaskBridge: stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
