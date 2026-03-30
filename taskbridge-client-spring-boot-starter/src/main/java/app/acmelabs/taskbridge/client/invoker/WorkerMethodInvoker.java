package app.acmelabs.taskbridge.client.invoker;

import app.acmelabs.taskbridge.client.dto.AcquiredJob;
import app.acmelabs.taskbridge.client.registry.WorkerEndpoint;
import app.acmelabs.taskbridge.client.result.ExternalWorkerResult;
import app.acmelabs.taskbridge.client.result.ExternalWorkerResultBuilder;
import app.acmelabs.taskbridge.client.result.WorkerSuccess;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

public class WorkerMethodInvoker {

    public ExternalWorkerResult invoke(WorkerEndpoint endpoint, AcquiredJob job) {
        Method method = endpoint.getMethod();
        method.setAccessible(true);

        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];

        for (int i = 0; i < params.length; i++) {
            Class<?> type = params[i].getType();
            if (type == AcquiredJob.class) {
                args[i] = job;
            } else if (type == ExternalWorkerResultBuilder.class) {
                args[i] = new ExternalWorkerResultBuilder();
            } else if (type == Map.class) {
                args[i] = job.variables();
            } else {
                throw new IllegalStateException(
                        "Unsupported parameter type " + type.getName() +
                        " in worker method " + method.getDeclaringClass().getName() +
                        "#" + method.getName() + ". Supported: AcquiredJob, ExternalWorkerResultBuilder, Map");
            }
        }

        Object returnValue;
        try {
            returnValue = method.invoke(endpoint.getBean(), args);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Worker method invocation failed", cause);
        }

        if (returnValue == null || method.getReturnType() == void.class) {
            return new WorkerSuccess();
        } else if (returnValue instanceof ExternalWorkerResult result) {
            return result;
        } else if (returnValue instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> variables = (Map<String, Object>) map;
            return new WorkerSuccess(variables);
        } else {
            throw new IllegalStateException(
                    "Unsupported return type " + returnValue.getClass().getName() +
                    " from worker method " + method.getDeclaringClass().getName() +
                    "#" + method.getName() + ". Supported: void, ExternalWorkerResult, Map");
        }
    }
}
