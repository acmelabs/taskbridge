package app.acmelabs.taskbridge.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskbridge.server")
public class TaskBridgeServerProperties {

    private boolean enabled = true;
    private String redisChannelPrefix = "taskbridge:";
    private ExclusiveAcquire exclusiveAcquire = new ExclusiveAcquire();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getRedisChannelPrefix() { return redisChannelPrefix; }
    public void setRedisChannelPrefix(String redisChannelPrefix) { this.redisChannelPrefix = redisChannelPrefix; }

    public ExclusiveAcquire getExclusiveAcquire() { return exclusiveAcquire; }
    public void setExclusiveAcquire(ExclusiveAcquire exclusiveAcquire) { this.exclusiveAcquire = exclusiveAcquire; }

    /**
     * Skips external worker jobs that Flowable would refuse to lock because their process instance is locked, so an
     * acquire batch is not rolled back by them. See {@code ExclusiveAwareExternalWorkerJobDataManager}. The lookahead
     * is how many extra rows a batch reads so that dropping later exclusive jobs of one process instance still fills it.
     */
    public static class ExclusiveAcquire {

        private boolean enabled = true;
        private int lookahead = 20;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getLookahead() { return lookahead; }
        public void setLookahead(int lookahead) { this.lookahead = lookahead; }
    }
}
