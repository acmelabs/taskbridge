package app.acmelabs.taskbridge.client.registry;

import java.lang.reflect.Method;

public class WorkerEndpoint {

    private final String id;
    private final String topic;
    private final String lockDuration;
    private final int maxJobs;
    private final int concurrency;
    private final long pollTimeoutMs;
    private final Object bean;
    private final Method method;

    public WorkerEndpoint(String id, String topic, String lockDuration, int maxJobs,
                          int concurrency, long pollTimeoutMs, Object bean, Method method) {
        this.id = id;
        this.topic = topic;
        this.lockDuration = lockDuration;
        this.maxJobs = maxJobs;
        this.concurrency = concurrency;
        this.pollTimeoutMs = pollTimeoutMs;
        this.bean = bean;
        this.method = method;
    }

    public String getId() { return id; }
    public String getTopic() { return topic; }
    public String getLockDuration() { return lockDuration; }
    public int getMaxJobs() { return maxJobs; }
    public int getConcurrency() { return concurrency; }
    public long getPollTimeoutMs() { return pollTimeoutMs; }
    public Object getBean() { return bean; }
    public Method getMethod() { return method; }
}
