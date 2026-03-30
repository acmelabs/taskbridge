package app.acmelabs.taskbridge.client.client;

import app.acmelabs.taskbridge.client.config.TaskBridgeClientProperties;
import app.acmelabs.taskbridge.client.dto.AcquiredJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;

public class LongPollClient {

    private static final Logger log = LoggerFactory.getLogger(LongPollClient.class);

    private final WebClient webClient;
    private final TaskBridgeClientProperties properties;

    public LongPollClient(WebClient webClient, TaskBridgeClientProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public Flux<AcquiredJob> poll(String topic, String lockDuration, int maxJobs, long timeoutMs) {
        log.debug("Polling topic={}, maxJobs={}, timeoutMs={}", topic, maxJobs, timeoutMs);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/taskbridge/jobs/poll")
                        .queryParam("topic", topic)
                        .queryParam("workerId", properties.getWorkerId())
                        .queryParam("maxJobs", maxJobs)
                        .queryParam("timeoutMs", timeoutMs)
                        .queryParam("lockDuration", lockDuration)
                        .build())
                .retrieve()
                .bodyToFlux(AcquiredJob.class)
                .timeout(Duration.ofMillis(timeoutMs + 5000))
                .onErrorResume(ex -> {
                    log.error("Long-poll error for topic={}: {}", topic, ex.getMessage());
                    return Flux.error(ex);
                });
    }
}
