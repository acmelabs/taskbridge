package app.acmelabs.taskbridge.client.result;

public record WorkerBpmnError(String errorCode, String errorMessage) implements ExternalWorkerResult {
}
