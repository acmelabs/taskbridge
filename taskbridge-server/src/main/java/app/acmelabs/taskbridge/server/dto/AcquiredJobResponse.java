package app.acmelabs.taskbridge.server.dto;

import java.util.Map;

public record AcquiredJobResponse(
        String jobId,
        String elementId,
        String elementName,
        String processInstanceId,
        String processDefinitionId,
        String tenantId,
        Map<String, Object> variables,
        int retries
) {}
