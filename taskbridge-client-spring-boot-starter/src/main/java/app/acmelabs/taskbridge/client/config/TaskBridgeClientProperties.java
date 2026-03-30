package app.acmelabs.taskbridge.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

@ConfigurationProperties(prefix = "taskbridge.client")
public class TaskBridgeClientProperties {

    private String baseUrl;
    private String workerId = "worker-" + UUID.randomUUID();
    private long pollTimeoutMs = 30000;
    private String lockDuration = "PT5M";
    private int maxJobs = 5;
    private int concurrency = 4;
    private int maxRetryAttempts = 3;
    private long retryBackoffMs = 5000;
    private long maxRetryBackoffMs = 30000;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }

    public long getPollTimeoutMs() { return pollTimeoutMs; }
    public void setPollTimeoutMs(long pollTimeoutMs) { this.pollTimeoutMs = pollTimeoutMs; }

    public String getLockDuration() { return lockDuration; }
    public void setLockDuration(String lockDuration) { this.lockDuration = lockDuration; }

    public int getMaxJobs() { return maxJobs; }
    public void setMaxJobs(int maxJobs) { this.maxJobs = maxJobs; }

    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int concurrency) { this.concurrency = concurrency; }

    public int getMaxRetryAttempts() { return maxRetryAttempts; }
    public void setMaxRetryAttempts(int maxRetryAttempts) { this.maxRetryAttempts = maxRetryAttempts; }

    public long getRetryBackoffMs() { return retryBackoffMs; }
    public void setRetryBackoffMs(long retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }

    public long getMaxRetryBackoffMs() { return maxRetryBackoffMs; }
    public void setMaxRetryBackoffMs(long maxRetryBackoffMs) { this.maxRetryBackoffMs = maxRetryBackoffMs; }
}
