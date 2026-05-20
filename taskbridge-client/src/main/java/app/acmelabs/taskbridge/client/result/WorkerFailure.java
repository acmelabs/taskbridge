package app.acmelabs.taskbridge.client.result;

public class WorkerFailure implements ExternalWorkerResult {

    private String errorMessage;
    private String errorDetails;
    private int retries;

    public WorkerFailure() {}

    public WorkerFailure(String errorMessage, String errorDetails, int retries) {
        this.errorMessage = errorMessage;
        this.errorDetails = errorDetails;
        this.retries = retries;
    }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getErrorDetails() { return errorDetails; }
    public void setErrorDetails(String errorDetails) { this.errorDetails = errorDetails; }

    public int getRetries() { return retries; }
    public void setRetries(int retries) { this.retries = retries; }
}
