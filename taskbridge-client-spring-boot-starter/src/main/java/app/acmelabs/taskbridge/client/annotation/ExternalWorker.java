package app.acmelabs.taskbridge.client.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(ExternalWorkers.class)
public @interface ExternalWorker {

    String id() default "";

    String topic();

    String lockDuration() default "";

    int maxJobs() default -1;

    int concurrency() default -1;

    long pollTimeoutMs() default -1;
}
