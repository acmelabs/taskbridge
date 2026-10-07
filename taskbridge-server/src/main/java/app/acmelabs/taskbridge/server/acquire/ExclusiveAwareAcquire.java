package app.acmelabs.taskbridge.server.acquire;

import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Installs the exclusive-aware external worker acquire on a process engine configuration. Needs two registrations:
 * the query lives in a MyBatis mapper, which belongs to the engine, and the data manager that runs it belongs to the
 * job service.
 */
public final class ExclusiveAwareAcquire {

    static final String MAPPER_RESOURCE = "app/acmelabs/taskbridge/server/acquire/ExclusiveAwareExternalWorkerJob.xml";

    private ExclusiveAwareAcquire() {
    }

    public static void install(ProcessEngineConfigurationImpl configuration, int lookahead) {
        Set<String> mappers = new LinkedHashSet<>();
        if (configuration.getCustomMybatisXMLMappers() != null) {
            mappers.addAll(configuration.getCustomMybatisXMLMappers());
        }
        mappers.add(MAPPER_RESOURCE);
        configuration.setCustomMybatisXMLMappers(mappers);
        configuration.addJobServiceConfigurator(new ExclusiveAwareAcquireConfigurator(lookahead));
    }
}
