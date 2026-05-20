package app.acmelabs.taskbridge.client.result;

public class WorkerBpmnError implements ExternalWorkerResult {

    private String errorCode;

    public WorkerBpmnError() {}

    public WorkerBpmnError(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
}
