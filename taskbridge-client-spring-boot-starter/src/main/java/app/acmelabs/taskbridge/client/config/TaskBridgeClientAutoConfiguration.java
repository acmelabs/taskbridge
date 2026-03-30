package app.acmelabs.taskbridge.client.config;

import app.acmelabs.taskbridge.client.client.JobLifecycleClient;
import app.acmelabs.taskbridge.client.client.LongPollClient;
import app.acmelabs.taskbridge.client.invoker.WorkerMethodInvoker;
import app.acmelabs.taskbridge.client.registry.ExternalWorkerBeanPostProcessor;
import app.acmelabs.taskbridge.client.registry.WorkerEndpointRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@AutoConfiguration
@EnableConfigurationProperties(TaskBridgeClientProperties.class)
@ConditionalOnProperty(prefix = "taskbridge.client", name = "base-url")
public class TaskBridgeClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "taskBridgeWebClient")
    public WebClient taskBridgeWebClient(TaskBridgeClientProperties props) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(props.getPollTimeoutMs() + 10000));
        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public LongPollClient taskBridgeLongPollClient(
            @Qualifier("taskBridgeWebClient") WebClient webClient,
            TaskBridgeClientProperties props) {
        return new LongPollClient(webClient, props);
    }

    @Bean
    @ConditionalOnMissingBean
    public JobLifecycleClient taskBridgeJobLifecycleClient(
            @Qualifier("taskBridgeWebClient") WebClient webClient,
            TaskBridgeClientProperties props) {
        return new JobLifecycleClient(webClient, props);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkerMethodInvoker taskBridgeWorkerMethodInvoker() {
        return new WorkerMethodInvoker();
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkerEndpointRegistry taskBridgeWorkerEndpointRegistry(
            LongPollClient longPollClient,
            JobLifecycleClient lifecycleClient,
            WorkerMethodInvoker invoker,
            TaskBridgeClientProperties props) {
        return new WorkerEndpointRegistry(longPollClient, lifecycleClient, invoker, props);
    }

    @Bean
    public ExternalWorkerBeanPostProcessor taskBridgeExternalWorkerBeanPostProcessor(
            TaskBridgeClientProperties props,
            WorkerEndpointRegistry registry) {
        return new ExternalWorkerBeanPostProcessor(props, registry);
    }

    @Bean
    public TaskBridgeLifecycle taskBridgeLifecycle(WorkerEndpointRegistry registry) {
        return new TaskBridgeLifecycle(registry);
    }
}
