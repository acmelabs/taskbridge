package app.acmelabs.taskbridge.server;

import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.job.service.impl.persistence.entity.ExternalWorkerJobEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

public class ExternalJobCreatedListener implements FlowableEventListener {

    private static final Logger log = LoggerFactory.getLogger(ExternalJobCreatedListener.class);

    private final StringRedisTemplate redisTemplate;
    private final String channelPrefix;

    public ExternalJobCreatedListener(StringRedisTemplate redisTemplate, String channelPrefix) {
        this.redisTemplate = redisTemplate;
        this.channelPrefix = channelPrefix;
    }

    @Override
    public void onEvent(FlowableEvent event) {
        if (event.getType() != FlowableEngineEventType.ENTITY_CREATED) {
            return;
        }
        if (!(event instanceof FlowableEntityEvent entityEvent)) {
            return;
        }
        if (!(entityEvent.getEntity() instanceof ExternalWorkerJobEntity job)) {
            return;
        }
        String topic = job.getJobHandlerConfiguration();
        try {
            redisTemplate.convertAndSend(channelPrefix + topic, "work_available");
            log.info("Published Redis wakeup for topic={}", topic);
        } catch (Exception e) {
            log.warn("Failed to publish Redis wakeup for topic={}: {}", topic, e.getMessage());
        }
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return true;
    }

    @Override
    public String getOnTransaction() {
        return "COMMITTED";
    }
}
