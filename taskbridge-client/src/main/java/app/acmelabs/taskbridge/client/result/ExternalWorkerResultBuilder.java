package app.acmelabs.taskbridge.client.result;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public class ExternalWorkerResultBuilder {

    private final ObjectMapper objectMapper;

    public ExternalWorkerResultBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SuccessBuilder success() {
        return new SuccessBuilder(objectMapper);
    }

    public FailureBuilder failure() {
        return new FailureBuilder();
    }

    public BpmnErrorBuilder bpmnError(String errorCode) {
        return new BpmnErrorBuilder(errorCode);
    }

    public static class SuccessBuilder {
        private final ObjectMapper objectMapper;
        private final Map<String, Object> variables = new LinkedHashMap<>();

        SuccessBuilder(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        public SuccessBuilder variable(String name, Object value) {
            variables.put(name, value);
            return this;
        }

        public SuccessBuilder variables(Map<String, Object> vars) {
            variables.putAll(vars);
            return this;
        }

        /** Converts a POJO to a JsonNode so it is serialised as a Flowable json-type variable. */
        public SuccessBuilder convertAndAddJsonVariable(String name, Object value) {
            variables.put(name, objectMapper.valueToTree(value));
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

        public FailureBuilder message(String message) {
            this.message = message;
            return this;
        }

        public FailureBuilder details(String details) {
            this.details = details;
            return this;
        }

        public FailureBuilder retries(int retries) {
            this.retries = retries;
            return this;
        }

        public FailureBuilder error(Throwable t) {
            if (t != null) {
                this.message = t.getMessage();
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                this.details = sw.toString();
            }
            return this;
        }

        public WorkerFailure build() {
            return new WorkerFailure(message, details, retries);
        }
    }

    public static class BpmnErrorBuilder {
        private final String errorCode;

        BpmnErrorBuilder(String errorCode) {
            this.errorCode = errorCode;
        }

        public WorkerBpmnError build() {
            return new WorkerBpmnError(errorCode);
        }
    }
}
