package app.acmelabs.taskbridge.client.result;

public record WorkerFailure(String errorMessage, String errorDetails, int retries)
        implements ExternalWorkerResult {

    public WorkerFailure(String errorMessage) {
        this(errorMessage, null, -1);
    }
}
