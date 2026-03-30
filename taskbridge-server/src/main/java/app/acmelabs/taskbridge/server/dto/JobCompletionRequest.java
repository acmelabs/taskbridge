package app.acmelabs.taskbridge.server.dto;

import java.util.Map;

public record JobCompletionRequest(
        String workerId,
        Map<String, Object> variables
) {}
