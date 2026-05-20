package app.acmelabs.taskbridge.client.worker;

import app.acmelabs.taskbridge.client.client.FlowableRestClient;
import app.acmelabs.taskbridge.client.dto.AcquiredJob;
import app.acmelabs.taskbridge.client.result.ExternalWorkerResult;
import app.acmelabs.taskbridge.client.result.WorkerBpmnError;
import app.acmelabs.taskbridge.client.result.WorkerFailure;
import app.acmelabs.taskbridge.client.result.WorkerSuccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class TopicSubscription {

    private static final Logger log = LoggerFactory.getLogger(TopicSubscription.class);

    private final WorkerEndpoint endpoint;
    private final String workerId;
    private final FlowableRestClient flowableClient;
    private final WorkerMethodInvoker invoker;
    private final long fallbackPollIntervalMs;
    private final long acquireJitterMs;
    private final long retryBackoffMs;
    private final long maxRetryBackoffMs;

    private final Semaphore wakeupSignal = new Semaphore(0);
    private final Semaphore concurrencySemaphore;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private volatile boolean stopped = false;
    private volatile Thread pollThread;

    public TopicSubscription(WorkerEndpoint endpoint,
                             String workerId,
                             FlowableRestClient flowableClient,
                             WorkerMethodInvoker invoker,
                             long fallbackPollIntervalMs,
                             long acquireJitterMs,
                             long retryBackoffMs,
                             long maxRetryBackoffMs) {
        this.endpoint = endpoint;
        this.workerId = workerId;
        this.flowableClient = flowableClient;
        this.invoker = invoker;
        this.fallbackPollIntervalMs = fallbackPollIntervalMs;
        this.acquireJitterMs = acquireJitterMs;
        this.retryBackoffMs = retryBackoffMs;
        this.maxRetryBackoffMs = maxRetryBackoffMs;
        this.concurrencySemaphore = new Semaphore(endpoint.getConcurrency());
    }

    public void start() {
        pollThread = Thread.ofVirtual()
                .name("taskbridge-" + endpoint.getTopic())
                .start(this::run);
    }

    private void run() {
        long currentBackoff = 0;
        boolean firstIteration = true;

        while (!stopped) {
            try {
                if (!firstIteration) {
                    boolean signalled = wakeupSignal.tryAcquire(fallbackPollIntervalMs, TimeUnit.MILLISECONDS);
                    if (signalled && acquireJitterMs > 0) {
                        Thread.sleep(ThreadLocalRandom.current().nextLong(acquireJitterMs));
                    }
                }
                firstIteration = false;
                acquireAndDispatch();
                currentBackoff = 0;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                firstIteration = false;
                log.error("Acquire failed for topic={}: {}", endpoint.getTopic(), e.getMessage());
                try {
                    Thread.sleep(currentBackoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                currentBackoff = Math.min(currentBackoff + retryBackoffMs, maxRetryBackoffMs);
            }
        }
    }

    private void acquireAndDispatch() {
        List<AcquiredJob> jobs = flowableClient.acquireJobs(
                endpoint.getTopic(), endpoint.getLockDuration(),
                endpoint.getMaxJobs(), endpoint.getNumberOfRetries(), workerId);

        for (AcquiredJob job : jobs) {
            try {
                concurrencySemaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            executor.submit(() -> {
                try {
                    processJob(job);
                } finally {
                    concurrencySemaphore.release();
                }
            });
        }

        if (jobs.size() >= endpoint.getMaxJobs()) {
            wakeupSignal.release();
        }
    }

    private void processJob(AcquiredJob job) {
        ExternalWorkerResult result;
        try {
            result = invoker.invoke(endpoint, job);
        } catch (Exception e) {
            int retries = Math.max(job.getRetries() - 1, 0);
            try {
                flowableClient.failJob(job.getId(), workerId, e.getMessage(), stackTrace(e), retries);
            } catch (Exception ex) {
                log.error("Failed to report worker exception for jobId={}: {}", job.getId(), ex.getMessage());
            }
            return;
        }

        try {
            if (result instanceof WorkerSuccess s) {
                flowableClient.completeJob(job.getId(), workerId, s.getVariables());
            } else if (result instanceof WorkerFailure f) {
                int retries = f.getRetries() == -1
                        ? Math.max(job.getRetries() - 1, 0)
                        : f.getRetries();
                flowableClient.failJob(job.getId(), workerId,
                        f.getErrorMessage(), f.getErrorDetails(), retries);
            } else if (result instanceof WorkerBpmnError e) {
                flowableClient.bpmnError(job.getId(), workerId, e.getErrorCode());
            }
        } catch (Exception e) {
            log.error("Failed to report result for jobId={}: {}", job.getId(), e.getMessage());
        }
    }

    public void wakeUp() {
        wakeupSignal.release();
    }

    public void stop() {
        stopped = true;
        if (pollThread != null) {
            pollThread.interrupt();
        }
        executor.close();
    }

    private static String stackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
