package app.acmelabs.taskbridge.client.dto;

import java.util.Map;

public record AcquiredJob(
        String jobId,
        String elementId,
        String elementName,
        String processInstanceId,
        String processDefinitionId,
        String tenantId,
        Map<String, Object> variables,
        int retries
) {
}
