package app.acmelabs.taskbridge.client.registry;

import app.acmelabs.taskbridge.client.annotation.ExternalWorker;
import app.acmelabs.taskbridge.client.config.TaskBridgeClientProperties;
import app.acmelabs.taskbridge.client.dto.AcquiredJob;
import app.acmelabs.taskbridge.client.result.ExternalWorkerResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ExternalWorkerBeanPostProcessor implements BeanPostProcessor, SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ExternalWorkerBeanPostProcessor.class);

    private static final List<Class<?>> SUPPORTED_PARAM_TYPES =
            List.of(AcquiredJob.class, ExternalWorkerResultBuilder.class, Map.class);

    private final List<WorkerEndpoint> discoveredEndpoints = new ArrayList<>();
    private final TaskBridgeClientProperties properties;
    private final WorkerEndpointRegistry registry;

    public ExternalWorkerBeanPostProcessor(TaskBridgeClientProperties properties,
                                           WorkerEndpointRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        for (Method method : targetClass.getDeclaredMethods()) {
            ExternalWorker[] annotations = method.getAnnotationsByType(ExternalWorker.class);
            for (ExternalWorker annotation : annotations) {
                validateMethod(method);
                WorkerEndpoint endpoint = buildEndpoint(annotation, bean, method);
                discoveredEndpoints.add(endpoint);
                log.debug("Discovered @ExternalWorker method: {}#{} -> topic={}",
                        targetClass.getSimpleName(), method.getName(), endpoint.getTopic());
            }
        }
        return bean;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (WorkerEndpoint endpoint : discoveredEndpoints) {
            registry.register(endpoint);
        }
        String topics = discoveredEndpoints.stream()
                .map(WorkerEndpoint::getTopic)
                .collect(Collectors.joining(", "));
        log.info("TaskBridge: discovered {} external worker endpoints for topics: {}",
                discoveredEndpoints.size(), topics);
    }

    private void validateMethod(Method method) {
        if (!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalStateException(
                    "@ExternalWorker method must be public: " +
                    method.getDeclaringClass().getName() + "#" + method.getName());
        }
        for (Class<?> paramType : Arrays.stream(method.getParameterTypes()).toList()) {
            boolean supported = SUPPORTED_PARAM_TYPES.stream()
                    .anyMatch(t -> t.isAssignableFrom(paramType));
            if (!supported) {
                throw new IllegalStateException(
                        "Unsupported parameter type " + paramType.getName() +
                        " in @ExternalWorker method " + method.getDeclaringClass().getName() +
                        "#" + method.getName());
            }
        }
    }

    private WorkerEndpoint buildEndpoint(ExternalWorker annotation, Object bean, Method method) {
        String topic = annotation.topic();

        String lockDuration = annotation.lockDuration().isBlank()
                ? properties.getLockDuration()
                : annotation.lockDuration();
        int maxJobs = annotation.maxJobs() > 0
                ? annotation.maxJobs()
                : properties.getMaxJobs();
        int concurrency = annotation.concurrency() > 0
                ? annotation.concurrency()
                : properties.getConcurrency();
        long pollTimeoutMs = annotation.pollTimeoutMs() > 0
                ? annotation.pollTimeoutMs()
                : properties.getPollTimeoutMs();
        String id = annotation.id().isBlank()
                ? "worker-" + topic + "-" + UUID.randomUUID()
                : annotation.id();

        return new WorkerEndpoint(id, topic, lockDuration, maxJobs, concurrency, pollTimeoutMs, bean, method);
    }
}
