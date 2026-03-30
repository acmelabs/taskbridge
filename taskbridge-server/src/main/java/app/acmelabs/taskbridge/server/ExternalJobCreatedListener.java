package app.acmelabs.taskbridge.server;

import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.job.service.impl.persistence.entity.ExternalWorkerJobEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalJobCreatedListener implements FlowableEventListener {

    private static final Logger log = LoggerFactory.getLogger(ExternalJobCreatedListener.class);

    private final LongPollRegistry longPollRegistry;

    public ExternalJobCreatedListener(LongPollRegistry longPollRegistry) {
        this.longPollRegistry = longPollRegistry;
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
        longPollRegistry.wakeUp(topic);
        log.info("External worker job created: jobId={}, topic={}, waking parked poll",
                job.getId(), topic);
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
