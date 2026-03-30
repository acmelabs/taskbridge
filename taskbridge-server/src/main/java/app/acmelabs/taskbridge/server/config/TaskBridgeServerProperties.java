package app.acmelabs.taskbridge.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskbridge.server")
public class TaskBridgeServerProperties {

    private long defaultTimeoutMs = 30000;
    private String defaultLockDuration = "PT5M";
    private int maxJobsPerPoll = 5;
    private boolean enabled = true;

    public long getDefaultTimeoutMs() { return defaultTimeoutMs; }
    public void setDefaultTimeoutMs(long defaultTimeoutMs) { this.defaultTimeoutMs = defaultTimeoutMs; }

    public String getDefaultLockDuration() { return defaultLockDuration; }
    public void setDefaultLockDuration(String defaultLockDuration) { this.defaultLockDuration = defaultLockDuration; }

    public int getMaxJobsPerPoll() { return maxJobsPerPoll; }
    public void setMaxJobsPerPoll(int maxJobsPerPoll) { this.maxJobsPerPoll = maxJobsPerPoll; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
