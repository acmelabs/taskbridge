package app.acmelabs.taskbridge.client.worker;

import java.util.List;

public class WorkerEndpointRegistry {

    private final List<WorkerEndpoint> endpoints = new java.util.ArrayList<>();

    public void register(WorkerEndpoint endpoint) {
        endpoints.add(endpoint);
    }

    public List<WorkerEndpoint> getEndpoints() {
        return java.util.Collections.unmodifiableList(endpoints);
    }
}
