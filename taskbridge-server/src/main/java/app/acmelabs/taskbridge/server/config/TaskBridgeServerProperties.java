package app.acmelabs.taskbridge.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskbridge.server")
public class TaskBridgeServerProperties {

    private boolean enabled = true;
    private String redisChannelPrefix = "taskbridge:";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getRedisChannelPrefix() { return redisChannelPrefix; }
    public void setRedisChannelPrefix(String redisChannelPrefix) { this.redisChannelPrefix = redisChannelPrefix; }
}
