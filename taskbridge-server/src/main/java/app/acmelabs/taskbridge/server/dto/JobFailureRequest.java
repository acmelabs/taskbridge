package app.acmelabs.taskbridge.server.dto;

public record JobFailureRequest(
        String workerId,
        String errorMessage,
        String errorDetails,
        int retries
) {}
