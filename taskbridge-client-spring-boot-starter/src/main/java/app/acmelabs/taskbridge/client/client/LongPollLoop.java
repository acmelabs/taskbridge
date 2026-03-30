package app.acmelabs.taskbridge.client.client;

import app.acmelabs.taskbridge.client.config.TaskBridgeClientProperties;
import app.acmelabs.taskbridge.client.dto.AcquiredJob;
import app.acmelabs.taskbridge.client.invoker.WorkerMethodInvoker;
import app.acmelabs.taskbridge.client.registry.WorkerEndpoint;
import app.acmelabs.taskbridge.client.result.ExternalWorkerResult;
import app.acmelabs.taskbridge.client.result.WorkerBpmnError;
import app.acmelabs.taskbridge.client.result.WorkerFailure;
import app.acmelabs.taskbridge.client.result.WorkerSuccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;

public class LongPollLoop {

    private static final Logger log = LoggerFactory.getLogger(LongPollLoop.class);

    private final WorkerEndpoint endpoint;
    private final LongPollClient longPollClient;
    private final JobLifecycleClient lifecycleClient;
    private final WorkerMethodInvoker invoker;
    private final TaskBridgeClientProperties properties;

    public LongPollLoop(WorkerEndpoint endpoint,
                        LongPollClient longPollClient,
                        JobLifecycleClient lifecycleClient,
                        WorkerMethodInvoker invoker,
                        TaskBridgeClientProperties properties) {
        this.endpoint = endpoint;
        this.longPollClient = longPollClient;
        this.lifecycleClient = lifecycleClient;
        this.invoker = invoker;
        this.properties = properties;
    }

    public Disposable start() {
        return Mono.defer(() ->
                longPollClient.poll(
                                endpoint.getTopic(),
                                endpoint.getLockDuration(),
                                endpoint.getMaxJobs(),
                                endpoint.getPollTimeoutMs())
                        .flatMap(job ->
                                Mono.fromCallable(() -> invoker.invoke(endpoint, job))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .flatMap(result -> handleResult(job, result))
                                        .onErrorResume(ex -> {
                                            log.error("TaskBridge: worker error for topic={}, jobId={}",
                                                    endpoint.getTopic(), job.jobId(), ex);
                                            int retries = Math.max(job.retries() - 1, 0);
                                            return lifecycleClient.fail(
                                                    job.jobId(), ex.getMessage(), null, retries);
                                        }),
                                endpoint.getConcurrency())
                        .then())
                .repeat()
                .retryWhen(Retry.backoff(Long.MAX_VALUE,
                                Duration.ofMillis(properties.getRetryBackoffMs()))
                        .maxBackoff(Duration.ofMillis(properties.getMaxRetryBackoffMs()))
                        .doBeforeRetry(signal -> log.warn(
                                "TaskBridge: poll loop retry for topic={}, attempt={}",
                                endpoint.getTopic(), signal.totalRetries())))
                .subscribe();
    }

    private Mono<Void> handleResult(AcquiredJob job, ExternalWorkerResult result) {
        if (result instanceof WorkerSuccess s) {
            return lifecycleClient.complete(job.jobId(), s.variables());
        } else if (result instanceof WorkerFailure f) {
            int retries = f.retries() == -1 ? Math.max(job.retries() - 1, 0) : f.retries();
            return lifecycleClient.fail(job.jobId(), f.errorMessage(), f.errorDetails(), retries);
        } else if (result instanceof WorkerBpmnError e) {
            return lifecycleClient.bpmnError(job.jobId(), e.errorCode(), e.errorMessage());
        } else {
            return Mono.error(new IllegalStateException("Unknown result type: " + result.getClass()));
        }
    }
}
