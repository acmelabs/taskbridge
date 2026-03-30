package app.acmelabs.taskbridge.client.result;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExternalWorkerResultBuilder {

    public SuccessBuilder success() {
        return new SuccessBuilder();
    }

    public FailureBuilder failure() {
        return new FailureBuilder();
    }

    public BpmnErrorBuilder bpmnError(String errorCode) {
        return new BpmnErrorBuilder(errorCode);
    }

    public static class SuccessBuilder {
        private final Map<String, Object> variables = new LinkedHashMap<>();

        public SuccessBuilder variable(String name, Object value) {
            variables.put(name, value);
            return this;
        }

        public SuccessBuilder variables(Map<String, Object> vars) {
            variables.putAll(vars);
            return this;
        }

        public WorkerSuccess build() {
            return new WorkerSuccess(variables);
        }
    }

    public static class FailureBuilder {
        private String message;
        private String details;
        private int retries = -1;

        public FailureBuilder message(String msg) {
            this.message = msg;
            return this;
        }

        public FailureBuilder details(String det) {
            this.details = det;
            return this;
        }

        public FailureBuilder retries(int r) {
            this.retries = r;
            return this;
        }

        public WorkerFailure build() {
            return new WorkerFailure(message, details, retries);
        }
    }

    public static class BpmnErrorBuilder {
        private final String errorCode;
        private String message;

        public BpmnErrorBuilder(String errorCode) {
            this.errorCode = errorCode;
        }

        public BpmnErrorBuilder message(String msg) {
            this.message = msg;
            return this;
        }

        public WorkerBpmnError build() {
            return new WorkerBpmnError(errorCode, message);
        }
    }
}
