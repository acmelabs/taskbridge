package app.acmelabs.taskbridge.client.worker;

import java.lang.reflect.Method;

public class WorkerEndpoint {

    private final String topic;
    private final String lockDuration;
    private final int maxJobs;
    private final int concurrency;
    private final int numberOfRetries;
    private final Object bean;
    private final Method method;

    public WorkerEndpoint(String topic, String lockDuration, int maxJobs, int concurrency,
                          int numberOfRetries, Object bean, Method method) {
        this.topic = topic;
        this.lockDuration = lockDuration;
        this.maxJobs = maxJobs;
        this.concurrency = concurrency;
        this.numberOfRetries = numberOfRetries;
        this.bean = bean;
        this.method = method;
    }

    public String getTopic() { return topic; }
    public String getLockDuration() { return lockDuration; }
    public int getMaxJobs() { return maxJobs; }
    public int getConcurrency() { return concurrency; }
    public int getNumberOfRetries() { return numberOfRetries; }
    public Object getBean() { return bean; }
    public Method getMethod() { return method; }
}
