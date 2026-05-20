package app.acmelabs.taskbridge.client.worker;

import app.acmelabs.taskbridge.client.annotation.ExternalWorker;
import app.acmelabs.taskbridge.client.config.TaskBridgeClientProperties;
import app.acmelabs.taskbridge.client.dto.AcquiredJob;
import app.acmelabs.taskbridge.client.result.ExternalWorkerResult;
import app.acmelabs.taskbridge.client.result.ExternalWorkerResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ExternalWorkerBeanPostProcessor implements BeanPostProcessor, SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ExternalWorkerBeanPostProcessor.class);

    private static final List<Class<?>> SUPPORTED_PARAM_TYPES =
            List.of(AcquiredJob.class, ExternalWorkerResultBuilder.class, Map.class);

    private static final List<Class<?>> SUPPORTED_RETURN_TYPES =
            List.of(void.class, Void.class, ExternalWorkerResult.class, Map.class);

    private final List<WorkerEndpoint> discovered = new ArrayList<>();
    private final Supplier<TaskBridgeClientProperties> propertiesSupplier;
    private final Supplier<WorkerEndpointRegistry> registrySupplier;

    /** Production constructor — uses ObjectProvider for lazy resolution to avoid BeanPostProcessor cycle warnings. */
    public ExternalWorkerBeanPostProcessor(ObjectProvider<TaskBridgeClientProperties> propertiesProvider,
                                           ObjectProvider<WorkerEndpointRegistry> registryProvider) {
        this.propertiesSupplier = propertiesProvider::getObject;
        this.registrySupplier = registryProvider::getObject;
    }

    /** Convenience constructor for unit tests. */
    public ExternalWorkerBeanPostProcessor(TaskBridgeClientProperties properties,
                                           WorkerEndpointRegistry registry) {
        this.propertiesSupplier = () -> properties;
        this.registrySupplier = () -> registry;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> target = AopUtils.getTargetClass(bean);
        for (Method method : target.getDeclaredMethods()) {
            ExternalWorker[] annotations = method.getAnnotationsByType(ExternalWorker.class);
            for (ExternalWorker annotation : annotations) {
                validate(method, annotation);
                discovered.add(buildEndpoint(annotation, bean, method));
                log.debug("Discovered @ExternalWorker: {}#{} -> topic={}",
                        target.getSimpleName(), method.getName(), annotation.topic());
            }
        }
        return bean;
    }

    @Override
    public void afterSingletonsInstantiated() {
        WorkerEndpointRegistry registry = registrySupplier.get();
        for (WorkerEndpoint endpoint : discovered) {
            registry.register(endpoint);
        }
        String topics = discovered.stream().map(WorkerEndpoint::getTopic).collect(Collectors.joining(", "));
        log.info("TaskBridge: registered {} worker endpoint(s): [{}]", discovered.size(), topics);
    }

    private void validate(Method method, ExternalWorker annotation) {
        String location = method.getDeclaringClass().getName() + "#" + method.getName();

        if (!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalStateException("@ExternalWorker method must be public: " + location);
        }

        if (annotation.topic().isBlank()) {
            throw new IllegalStateException("@ExternalWorker topic must not be blank: " + location);
        }

        for (Class<?> paramType : Arrays.asList(method.getParameterTypes())) {
            boolean supported = SUPPORTED_PARAM_TYPES.stream().anyMatch(t -> t.isAssignableFrom(paramType));
            if (!supported) {
                throw new IllegalStateException(
                        "Unsupported parameter type " + paramType.getName() +
                        " in @ExternalWorker method " + location +
                        ". Supported: AcquiredJob, ExternalWorkerResultBuilder, Map");
            }
        }

        Class<?> returnType = method.getReturnType();
        boolean validReturn = SUPPORTED_RETURN_TYPES.stream().anyMatch(t -> t.isAssignableFrom(returnType));
        if (!validReturn) {
            throw new IllegalStateException(
                    "Unsupported return type " + returnType.getName() +
                    " in @ExternalWorker method " + location +
                    ". Supported: void, ExternalWorkerResult, Map");
        }
    }

    private WorkerEndpoint buildEndpoint(ExternalWorker ann, Object bean, Method method) {
        TaskBridgeClientProperties props = propertiesSupplier.get();

        String lockDuration = ann.lockDuration().isBlank() ? props.getLockDuration() : ann.lockDuration();
        int maxJobs = ann.maxJobs() > 0 ? ann.maxJobs() : props.getMaxJobs();
        int concurrency = ann.concurrency() > 0 ? ann.concurrency() : props.getConcurrency();
        int numberOfRetries = ann.numberOfRetries() > 0 ? ann.numberOfRetries() : props.getNumberOfRetries();

        return new WorkerEndpoint(ann.topic(), lockDuration, maxJobs, concurrency, numberOfRetries, bean, method);
    }
}
