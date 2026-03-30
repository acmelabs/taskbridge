package app.acmelabs.taskbridge.client.annotation;

import app.acmelabs.taskbridge.client.config.TaskBridgeClientAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(TaskBridgeClientAutoConfiguration.class)
public @interface EnableExternalWorkers {
}
