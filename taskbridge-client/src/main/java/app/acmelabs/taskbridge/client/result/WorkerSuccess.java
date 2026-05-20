package app.acmelabs.taskbridge.client.result;

import java.util.Collections;
import java.util.Map;

public class WorkerSuccess implements ExternalWorkerResult {

    private Map<String, Object> variables;

    public WorkerSuccess() {
        this.variables = Collections.emptyMap();
    }

    public WorkerSuccess(Map<String, Object> variables) {
        this.variables = variables;
    }

    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> variables) { this.variables = variables; }
}
