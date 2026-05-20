package app.acmelabs.taskbridge.client.config;

import app.acmelabs.taskbridge.client.client.FlowableRestClient;
import app.acmelabs.taskbridge.client.worker.ExternalWorkerBeanPostProcessor;
import app.acmelabs.taskbridge.client.worker.TaskBridgeLifecycle;
import app.acmelabs.taskbridge.client.worker.WorkerEndpointRegistry;
import app.acmelabs.taskbridge.client.worker.WorkerMethodInvoker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@AutoConfiguration
@ConditionalOnProperty(prefix = "taskbridge.client", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TaskBridgeClientProperties.class)
public class TaskBridgeClientAutoConfiguration {

    @Bean
    public WorkerEndpointRegistry workerEndpointRegistry() {
        return new WorkerEndpointRegistry();
    }

    @Bean
    public WorkerMethodInvoker workerMethodInvoker(ObjectMapper objectMapper) {
        return new WorkerMethodInvoker(objectMapper);
    }

    @Bean
    public ExternalWorkerBeanPostProcessor externalWorkerBeanPostProcessor(
            ObjectProvider<TaskBridgeClientProperties> properties,
            ObjectProvider<WorkerEndpointRegistry> registry) {
        return new ExternalWorkerBeanPostProcessor(properties, registry);
    }

    @Bean
    public FlowableRestClient flowableRestClient(TaskBridgeClientProperties properties,
                                                 ObjectMapper objectMapper) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getFlowableBaseUrl());
        if (StringUtils.hasText(properties.getUsername())) {
            String credentials = properties.getUsername() + ":" + properties.getPassword();
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        }
        return new FlowableRestClient(builder.build(), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(RedisMessageListenerContainer.class)
    public RedisMessageListenerContainer taskBridgeRedisListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    @Bean
    public TaskBridgeLifecycle taskBridgeLifecycle(
            WorkerEndpointRegistry registry,
            FlowableRestClient flowableRestClient,
            WorkerMethodInvoker workerMethodInvoker,
            TaskBridgeClientProperties properties,
            ObjectProvider<RedisMessageListenerContainer> listenerContainerProvider) {
        return new TaskBridgeLifecycle(registry, flowableRestClient, workerMethodInvoker,
                properties, listenerContainerProvider);
    }
}
