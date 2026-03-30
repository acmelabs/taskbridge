package app.acmelabs.taskbridge.server.config;

import app.acmelabs.taskbridge.server.ExternalJobCreatedListener;
import app.acmelabs.taskbridge.server.ExternalJobLifecycleController;
import app.acmelabs.taskbridge.server.ExternalJobLongPollController;
import app.acmelabs.taskbridge.server.LongPollRegistry;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.ManagementService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@ConditionalOnProperty(prefix = "taskbridge.server", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TaskBridgeServerProperties.class)
public class TaskBridgeServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LongPollRegistry taskBridgeLongPollRegistry() {
        return new LongPollRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public ExternalJobCreatedListener taskBridgeExternalJobCreatedListener(LongPollRegistry registry) {
        return new ExternalJobCreatedListener(registry);
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

    @Bean
    @ConditionalOnMissingBean
    public ExternalJobLongPollController taskBridgeLongPollController(
            ManagementService managementService,
            LongPollRegistry registry,
            TaskBridgeServerProperties properties) {
        return new ExternalJobLongPollController(managementService, registry, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExternalJobLifecycleController taskBridgeLifecycleController(
            ManagementService managementService) {
        return new ExternalJobLifecycleController(managementService);
    }
}
