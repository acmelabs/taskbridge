package app.acmelabs.taskbridge.client.registry;

import app.acmelabs.taskbridge.client.client.JobLifecycleClient;
import app.acmelabs.taskbridge.client.client.LongPollClient;
import app.acmelabs.taskbridge.client.client.LongPollLoop;
import app.acmelabs.taskbridge.client.config.TaskBridgeClientProperties;
import app.acmelabs.taskbridge.client.invoker.WorkerMethodInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class WorkerEndpointRegistry {

    private static final Logger log = LoggerFactory.getLogger(WorkerEndpointRegistry.class);

    private final List<WorkerEndpoint> endpoints = new CopyOnWriteArrayList<>();
    private final LongPollClient longPollClient;
    private final JobLifecycleClient lifecycleClient;
    private final WorkerMethodInvoker invoker;
    private final TaskBridgeClientProperties properties;
    private final Map<String, Disposable> activeLoops = new ConcurrentHashMap<>();

    public WorkerEndpointRegistry(LongPollClient longPollClient,
                                   JobLifecycleClient lifecycleClient,
                                   WorkerMethodInvoker invoker,
                                   TaskBridgeClientProperties properties) {
        this.longPollClient = longPollClient;
        this.lifecycleClient = lifecycleClient;
        this.invoker = invoker;
        this.properties = properties;
    }

    public void register(WorkerEndpoint endpoint) {
        endpoints.add(endpoint);
    }

    public void startAll() {
        for (WorkerEndpoint endpoint : endpoints) {
            LongPollLoop loop = new LongPollLoop(
                    endpoint, longPollClient, lifecycleClient, invoker, properties);
            Disposable disposable = loop.start();
            activeLoops.put(endpoint.getId(), disposable);
            log.info("TaskBridge: started poll loop for topic={}", endpoint.getTopic());
        }
    }

    public void stop() {
        activeLoops.values().forEach(Disposable::dispose);
        activeLoops.clear();
        log.info("TaskBridge: stopped all poll loops");
    }
}
