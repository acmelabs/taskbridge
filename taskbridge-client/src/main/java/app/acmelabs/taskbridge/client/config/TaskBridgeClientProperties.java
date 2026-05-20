package app.acmelabs.taskbridge.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

@ConfigurationProperties(prefix = "taskbridge.client")
public class TaskBridgeClientProperties {

    private String flowableBaseUrl;
    private String workerId = "worker-" + UUID.randomUUID();
    private String redisChannelPrefix = "taskbridge:";

    private String lockDuration = "PT5M";
    private int maxJobs = 5;
    private int concurrency = 4;
    private int numberOfRetries = 3;

    private long fallbackPollIntervalMs = 30_000;
    private long acquireJitterMs = 100;
    private long retryBackoffMs = 5_000;
    private long maxRetryBackoffMs = 30_000;

    private String username = "";
    private String password = "";

    public String getFlowableBaseUrl() { return flowableBaseUrl; }
    public void setFlowableBaseUrl(String flowableBaseUrl) { this.flowableBaseUrl = flowableBaseUrl; }

    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }

    public String getRedisChannelPrefix() { return redisChannelPrefix; }
    public void setRedisChannelPrefix(String redisChannelPrefix) { this.redisChannelPrefix = redisChannelPrefix; }

    public String getLockDuration() { return lockDuration; }
    public void setLockDuration(String lockDuration) { this.lockDuration = lockDuration; }

    public int getMaxJobs() { return maxJobs; }
    public void setMaxJobs(int maxJobs) { this.maxJobs = maxJobs; }

    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int concurrency) { this.concurrency = concurrency; }

    public int getNumberOfRetries() { return numberOfRetries; }
    public void setNumberOfRetries(int numberOfRetries) { this.numberOfRetries = numberOfRetries; }

    public long getFallbackPollIntervalMs() { return fallbackPollIntervalMs; }
    public void setFallbackPollIntervalMs(long fallbackPollIntervalMs) { this.fallbackPollIntervalMs = fallbackPollIntervalMs; }

    public long getAcquireJitterMs() { return acquireJitterMs; }
    public void setAcquireJitterMs(long acquireJitterMs) { this.acquireJitterMs = acquireJitterMs; }

    public long getRetryBackoffMs() { return retryBackoffMs; }
    public void setRetryBackoffMs(long retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }

    public long getMaxRetryBackoffMs() { return maxRetryBackoffMs; }
    public void setMaxRetryBackoffMs(long maxRetryBackoffMs) { this.maxRetryBackoffMs = maxRetryBackoffMs; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
