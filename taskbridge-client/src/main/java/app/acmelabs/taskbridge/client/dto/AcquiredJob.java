package app.acmelabs.taskbridge.client.dto;

import java.time.Instant;
import java.util.Map;

public class AcquiredJob {

    private String id;
    private String correlationId;
    private int retries;

    private String scopeId;
    private String scopeType;
    private String subScopeId;
    private String scopeDefinitionId;

    private String tenantId;
    private String elementId;
    private String elementName;
    private String exceptionMessage;

    private Instant createTime;
    private Instant dueDate;
    private String workerId;
    private Instant lockExpirationTime;

    private Map<String, Object> variables;

    /** Set by the client after acquire — not present in the Flowable response. */
    private String topicName;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public int getRetries() { return retries; }
    public void setRetries(int retries) { this.retries = retries; }

    public String getScopeId() { return scopeId; }
    public void setScopeId(String scopeId) { this.scopeId = scopeId; }

    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }

    public String getSubScopeId() { return subScopeId; }
    public void setSubScopeId(String subScopeId) { this.subScopeId = subScopeId; }

    public String getScopeDefinitionId() { return scopeDefinitionId; }
    public void setScopeDefinitionId(String scopeDefinitionId) { this.scopeDefinitionId = scopeDefinitionId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getElementId() { return elementId; }
    public void setElementId(String elementId) { this.elementId = elementId; }

    public String getElementName() { return elementName; }
    public void setElementName(String elementName) { this.elementName = elementName; }

    public String getExceptionMessage() { return exceptionMessage; }
    public void setExceptionMessage(String exceptionMessage) { this.exceptionMessage = exceptionMessage; }

    public Instant getCreateTime() { return createTime; }
    public void setCreateTime(Instant createTime) { this.createTime = createTime; }

    public Instant getDueDate() { return dueDate; }
    public void setDueDate(Instant dueDate) { this.dueDate = dueDate; }

    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }

    public Instant getLockExpirationTime() { return lockExpirationTime; }
    public void setLockExpirationTime(Instant lockExpirationTime) { this.lockExpirationTime = lockExpirationTime; }

    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> variables) { this.variables = variables; }

    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) { this.topicName = topicName; }
}
