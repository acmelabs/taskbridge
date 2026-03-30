package app.acmelabs.taskbridge.client.config;

import app.acmelabs.taskbridge.client.registry.WorkerEndpointRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

public class TaskBridgeLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TaskBridgeLifecycle.class);

    private final WorkerEndpointRegistry registry;
    private volatile boolean running = false;

    public TaskBridgeLifecycle(WorkerEndpointRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void start() {
        registry.startAll();
        running = true;
        log.info("TaskBridge: external worker poll loops started");
    }

    @Override
    public void stop() {
        registry.stop();
        running = false;
        log.info("TaskBridge: external worker poll loops stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
