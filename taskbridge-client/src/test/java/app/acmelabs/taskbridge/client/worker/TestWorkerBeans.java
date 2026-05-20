package app.acmelabs.taskbridge.client.worker;

import app.acmelabs.taskbridge.client.annotation.ExternalWorker;
import app.acmelabs.taskbridge.client.dto.AcquiredJob;
import app.acmelabs.taskbridge.client.result.ExternalWorkerResult;
import app.acmelabs.taskbridge.client.result.ExternalWorkerResultBuilder;

import java.util.Map;

public final class TestWorkerBeans {
    private TestWorkerBeans() {}

    public static class SingleWorkerBean {
        @ExternalWorker(topic = "payment-task")
        public void process(AcquiredJob job) {}
    }

    public static class PlainBean {
        public void doSomething() {}
    }

    public static class MultiTopicBean {
        @ExternalWorker(topic = "topic-a")
        @ExternalWorker(topic = "topic-b")
        public void handle() {}
    }

    public static class ExplicitValuesBean {
        @ExternalWorker(topic = "explicit-topic", lockDuration = "PT1M", maxJobs = 2, concurrency = 1)
        public void handle() {}
    }

    public static class DefaultValuesBean {
        @ExternalWorker(topic = "default-topic")
        public void handle() {}
    }

    // --- Supported parameter types ---

    public static class AllParamTypesBean {
        @ExternalWorker(topic = "all-params")
        public void handle(AcquiredJob job, ExternalWorkerResultBuilder result, Map<String, Object> vars) {}
    }

    // --- Supported return types ---

    public static class ReturnsResultBean {
        @ExternalWorker(topic = "returns-result")
        public ExternalWorkerResult handle(ExternalWorkerResultBuilder result) {
            return result.success().build();
        }
    }

    public static class ReturnsMapBean {
        @ExternalWorker(topic = "returns-map")
        public Map<String, Object> handle() { return Map.of(); }
    }

    // --- Validation failures ---

    public static class PrivateWorkerBean {
        @ExternalWorker(topic = "private-topic")
        private void handle() {}
    }

    public static class BadParamBean {
        @ExternalWorker(topic = "bad-param")
        public void handle(String unsupported) {}
    }

    public static class BlankTopicBean {
        @ExternalWorker(topic = "")
        public void handle() {}
    }

    public static class BadReturnBean {
        @ExternalWorker(topic = "bad-return")
        public String handle() { return ""; }
    }
}
