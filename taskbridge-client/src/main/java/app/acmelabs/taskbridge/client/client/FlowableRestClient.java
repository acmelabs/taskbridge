package app.acmelabs.taskbridge.client.client;

import app.acmelabs.taskbridge.client.dto.AcquiredJob;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FlowableRestClient {

    private static final Logger log = LoggerFactory.getLogger(FlowableRestClient.class);

    static final String BASE_PATH = "/external-job-api";
    public static final String ACQUIRE_JOBS = "/acquire/jobs";
    public static final String ACQUIRE_JOBS_JOB_ID_COMPLETE = "/acquire/jobs/{jobId}/complete";
    public static final String ACQUIRE_JOBS_JOB_ID_FAIL = "/acquire/jobs/{jobId}/fail";
    public static final String ACQUIRE_JOBS_JOB_ID_BPMN_ERROR = "/acquire/jobs/{jobId}/bpmnError";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public FlowableRestClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Acquire
    // -------------------------------------------------------------------------

    public List<AcquiredJob> acquireJobs(String topic, String lockDuration, int maxJobs, int numberOfRetries, String workerId) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("topic", topic)
                .put("lockDuration", lockDuration)
                .put("numberOfTasks", maxJobs)
                .put("numberOfRetries", numberOfRetries)
                .put("workerId", workerId);

        String json = restClient.post()
                .uri(BASE_PATH + ACQUIRE_JOBS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        return parseJobs(json, topic);
    }

    // -------------------------------------------------------------------------
    // Complete / Fail / BPMN error
    // -------------------------------------------------------------------------

    public void completeJob(String jobId, String workerId, Map<String, Object> variables) {
        ObjectNode body = objectMapper.createObjectNode().put("workerId", workerId);
        body.set("variables", toVariableArray(variables));

        restClient.post()
                .uri(BASE_PATH + ACQUIRE_JOBS_JOB_ID_COMPLETE, jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public void failJob(String jobId, String workerId, String errorMessage, String errorDetails, int retries) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("workerId", workerId)
                .put("errorMessage", errorMessage)
                .put("errorDetails", errorDetails)
                .put("retries", retries);

        restClient.post()
                .uri(BASE_PATH + ACQUIRE_JOBS_JOB_ID_FAIL, jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public void bpmnError(String jobId, String workerId, String errorCode) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("workerId", workerId)
                .put("errorCode", errorCode);

        restClient.post()
                .uri(BASE_PATH + ACQUIRE_JOBS_JOB_ID_BPMN_ERROR, jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    // -------------------------------------------------------------------------
    // Response parsing: JSON → List<AcquiredJob>
    // -------------------------------------------------------------------------

    private List<AcquiredJob> parseJobs(String json, String topicName) {
        try {
            ArrayNode array = objectMapper.readValue(json, ArrayNode.class);
            List<AcquiredJob> jobs = new ArrayList<>(array.size());
            for (JsonNode node : array) {
                if (node.isObject()) {
                    AcquiredJob job = parseJob((ObjectNode) node);
                    job.setTopicName(topicName);
                    jobs.add(job);
                }
            }
            return jobs;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse acquire response: " + e.getMessage(), e);
        }
    }

    private AcquiredJob parseJob(ObjectNode n) {
        AcquiredJob job = new AcquiredJob();
        job.setId(str(n, "id"));
        job.setCorrelationId(str(n, "correlationId"));
        job.setRetries(n.path("retries").asInt(0));

        // BPMN jobs expose processInstanceId; CMMN jobs use generic scope fields
        if (n.hasNonNull("processInstanceId")) {
            job.setScopeId(str(n, "processInstanceId"));
            job.setScopeType("bpmn");
            job.setSubScopeId(str(n, "executionId"));
            job.setScopeDefinitionId(str(n, "processDefinitionId"));
        } else {
            job.setScopeId(str(n, "scopeId"));
            job.setScopeType(str(n, "scopeType"));
            job.setSubScopeId(str(n, "subScopeId"));
            job.setScopeDefinitionId(str(n, "scopeDefinitionId"));
        }

        job.setTenantId(str(n, "tenantId"));
        job.setElementId(str(n, "elementId"));
        job.setElementName(str(n, "elementName"));
        job.setExceptionMessage(str(n, "exceptionMessage"));
        job.setCreateTime(instant(str(n, "createTime")));
        job.setDueDate(instant(str(n, "dueDate")));
        job.setWorkerId(str(n, "lockOwner"));               // Flowable uses "lockOwner" in the response
        job.setLockExpirationTime(instant(str(n, "lockExpirationTime")));

        JsonNode vars = n.path("variables");
        job.setVariables(vars.isArray() ? parseVariables((ArrayNode) vars) : Collections.emptyMap());

        return job;
    }

    private Map<String, Object> parseVariables(ArrayNode array) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (JsonNode varNode : array) {
            if (!varNode.isObject()) continue;
            ObjectNode v = (ObjectNode) varNode;
            String name = str(v, "name");
            if (name == null || name.isBlank()) continue;
            result.put(name, parseVariableValue(str(v, "type"), v.path("value")));
        }
        return result;
    }

    private Object parseVariableValue(String type, JsonNode value) {
        if (value.isMissingNode() || value.isNull() || type == null) {
            return null;
        }
        return switch (type) {
            case "string"        -> value.asText();
            case "short"         -> value.shortValue();
            case "integer"       -> value.asInt();
            case "long"          -> value.asLong();
            case "double"        -> value.asDouble();
            case "boolean"       -> value.asBoolean();
            case "date"          -> Date.from(Instant.parse(value.asText()));
            case "instant"       -> Instant.parse(value.asText());
            case "localDate"     -> LocalDate.parse(value.asText());
            case "localDateTime" -> LocalDateTime.parse(value.asText());
            case "json"          -> value;
            default -> {
                log.warn("Unknown variable type '{}', skipping", type);
                yield null;
            }
        };
    }

    // -------------------------------------------------------------------------
    // Variable serialisation: Map<String, Object> → typed array
    // -------------------------------------------------------------------------

    ArrayNode toVariableArray(Map<String, Object> variables) {
        ArrayNode array = objectMapper.createArrayNode();
        if (variables == null) return array;
        variables.forEach((name, value) -> {
            ObjectNode entry = array.addObject();
            entry.put("name", name);
            serializeValue(entry, value);
        });
        return array;
    }

    private void serializeValue(ObjectNode entry, Object value) {
        if (value == null) {
            entry.put("type", "string").putNull("value");
        } else if (value instanceof String s) {
            entry.put("type", "string").put("value", s);
        } else if (value instanceof Short s) {
            entry.put("type", "short").put("value", s);
        } else if (value instanceof Integer i) {
            entry.put("type", "integer").put("value", i);
        } else if (value instanceof Long l) {
            entry.put("type", "long").put("value", l);
        } else if (value instanceof Double d) {
            entry.put("type", "double").put("value", d);
        } else if (value instanceof Boolean b) {
            entry.put("type", "boolean").put("value", b);
        } else if (value instanceof Date d) {
            entry.put("type", "date").put("value", d.toInstant().toString());
        } else if (value instanceof Instant i) {
            entry.put("type", "instant").put("value", i.toString());
        } else if (value instanceof LocalDate ld) {
            entry.put("type", "localDate").put("value", ld.toString());
        } else if (value instanceof LocalDateTime ldt) {
            entry.put("type", "localDateTime").put("value", ldt.toString());
        } else if (value instanceof JsonNode j) {
            entry.put("type", "json").set("value", j);
        } else {
            entry.put("type", "json").set("value", objectMapper.valueToTree(value));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String str(ObjectNode node, String field) {
        JsonNode n = node.path(field);
        return (n.isMissingNode() || n.isNull()) ? null : n.asText();
    }

    private static Instant instant(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
