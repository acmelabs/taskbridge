package app.acmelabs.taskbridge.server.config;

import app.acmelabs.taskbridge.server.ExternalJobCreatedListener;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@ConditionalOnProperty(prefix = "taskbridge.server", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TaskBridgeServerProperties.class)
public class TaskBridgeServerAutoConfiguration {

    @Bean
    public ExternalJobCreatedListener taskBridgeExternalJobCreatedListener(
            StringRedisTemplate redisTemplate,
            TaskBridgeServerProperties properties) {
        return new ExternalJobCreatedListener(redisTemplate, properties.getRedisChannelPrefix());
    }

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> taskBridgeListenerConfigurer(
            ExternalJobCreatedListener listener) {
        return config -> {
            List<FlowableEventListener> existing = config.getEventListeners();
            List<FlowableEventListener> merged = new ArrayList<>();
            if (existing != null) merged.addAll(existing);
            merged.add(listener);
            config.setEventListeners(merged);
        };
    }
}
