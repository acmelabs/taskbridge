package app.acmelabs.taskbridge.client.client;

import app.acmelabs.taskbridge.client.config.TaskBridgeClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

public class JobLifecycleClient {

    private static final Logger log = LoggerFactory.getLogger(JobLifecycleClient.class);

    private final WebClient webClient;
    private final TaskBridgeClientProperties properties;

    public JobLifecycleClient(WebClient webClient, TaskBridgeClientProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public Mono<Void> complete(String jobId, Map<String, Object> variables) {
        log.debug("Completing jobId={}", jobId);
        Map<String, Object> body = new HashMap<>();
        body.put("workerId", properties.getWorkerId());
        body.put("variables", variables);
        return webClient.post()
                .uri("/api/taskbridge/jobs/{jobId}/complete", jobId)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(ex -> log.error("Complete failed for jobId={}: {}", jobId, ex.getMessage()));
    }

    public Mono<Void> fail(String jobId, String errorMessage, String errorDetails, int retries) {
        log.debug("Failing jobId={}, retries={}", jobId, retries);
        Map<String, Object> body = new HashMap<>();
        body.put("workerId", properties.getWorkerId());
        body.put("errorMessage", errorMessage);
        body.put("errorDetails", errorDetails);
        body.put("retries", retries);
        return webClient.post()
                .uri("/api/taskbridge/jobs/{jobId}/fail", jobId)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(ex -> log.error("Fail failed for jobId={}: {}", jobId, ex.getMessage()));
    }

    public Mono<Void> bpmnError(String jobId, String errorCode, String errorMessage) {
        log.debug("BpmnError jobId={}, errorCode={}", jobId, errorCode);
        Map<String, Object> body = new HashMap<>();
        body.put("workerId", properties.getWorkerId());
        body.put("errorCode", errorCode);
        body.put("errorMessage", errorMessage);
        return webClient.post()
                .uri("/api/taskbridge/jobs/{jobId}/bpmn-error", jobId)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(ex -> log.error("BpmnError failed for jobId={}: {}", jobId, ex.getMessage()));
    }
}
