package app.acmelabs.taskbridge.client.result;

public sealed interface ExternalWorkerResult
        permits WorkerSuccess, WorkerFailure, WorkerBpmnError {
}
