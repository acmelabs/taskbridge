package app.acmelabs.taskbridge.server.dto;

public record JobBpmnErrorRequest(
        String workerId,
        String errorCode,
        String errorMessage
) {}
