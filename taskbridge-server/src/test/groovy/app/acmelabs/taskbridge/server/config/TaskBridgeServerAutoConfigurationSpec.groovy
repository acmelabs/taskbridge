package app.acmelabs.taskbridge.server.config

import app.acmelabs.taskbridge.server.ExternalJobCreatedListener
import org.flowable.spring.SpringProcessEngineConfiguration
import org.flowable.spring.boot.EngineConfigurationConfigurer
import org.mockito.ArgumentCaptor
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import spock.lang.Specification

import static org.mockito.Mockito.mock
import static org.mockito.Mockito.verify
import static org.mockito.Mockito.when

class TaskBridgeServerAutoConfigurationSpec extends Specification {

    ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskBridgeServerAutoConfiguration))
            .withUserConfiguration(MockRedisConfiguration)

    // -------------------------------------------------------------------------
    // Bean creation — enabled (default)
    // -------------------------------------------------------------------------

    def "creates ExternalJobCreatedListener and EngineConfigurationConfigurer when enabled by default"() {
        expect:
        runner.run { context ->
            assert context.startupFailure == null
            assert context.getBean(ExternalJobCreatedListener) != null
            assert !context.getBeansOfType(EngineConfigurationConfigurer).isEmpty()
        }
    }

    // -------------------------------------------------------------------------
    // Bean creation — disabled
    // -------------------------------------------------------------------------

    def "creates no beans when taskbridge.server.enabled=false"() {
        expect:
        runner.withPropertyValues("taskbridge.server.enabled=false").run { context ->
            assert context.startupFailure == null
            assert context.getBeansOfType(ExternalJobCreatedListener).isEmpty()
            assert context.getBeansOfType(EngineConfigurationConfigurer).isEmpty()
        }
    }

    // -------------------------------------------------------------------------
    // Configuration properties
    // -------------------------------------------------------------------------

    def "default redis-channel-prefix is 'taskbridge:'"() {
        expect:
        runner.run { context ->
            assert context.startupFailure == null
            assert context.getBean(TaskBridgeServerProperties).redisChannelPrefix == "taskbridge:"
        }
    }

    def "custom redis-channel-prefix is applied to properties"() {
        expect:
        runner.withPropertyValues("taskbridge.server.redis-channel-prefix=custom:").run { context ->
            assert context.startupFailure == null
            assert context.getBean(TaskBridgeServerProperties).redisChannelPrefix == "custom:"
        }
    }

    // -------------------------------------------------------------------------
    // EngineConfigurationConfigurer behaviour
    // -------------------------------------------------------------------------

    def "EngineConfigurationConfigurer appends the listener to engine event listeners"() {
        expect:
        runner.run { context ->
            assert context.startupFailure == null
            def configurer = context.getBean(EngineConfigurationConfigurer)
            def listener = context.getBean(ExternalJobCreatedListener)

            def config = mock(SpringProcessEngineConfiguration)
            when(config.getEventListeners()).thenReturn(null)
            configurer.configure(config)

            def captor = ArgumentCaptor.forClass(List)
            verify(config).setEventListeners(captor.capture())
            assert captor.value.contains(listener)
        }
    }

    // -------------------------------------------------------------------------
    // Stub — Redis beans required by the auto-configuration
    // -------------------------------------------------------------------------

    @Configuration
    static class MockRedisConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            def factory = mock(RedisConnectionFactory)
            def conn = mock(RedisConnection)
            when(factory.getConnection()).thenReturn(conn)
            factory
        }

        @Bean
        StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
            new StringRedisTemplate(factory)
        }
    }
}
