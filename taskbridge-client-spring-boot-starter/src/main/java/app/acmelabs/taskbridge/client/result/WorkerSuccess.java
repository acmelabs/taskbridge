package app.acmelabs.taskbridge.client.result;

import java.util.Collections;
import java.util.Map;

public record WorkerSuccess(Map<String, Object> variables) implements ExternalWorkerResult {

    public WorkerSuccess() {
        this(Collections.emptyMap());
    }
}
