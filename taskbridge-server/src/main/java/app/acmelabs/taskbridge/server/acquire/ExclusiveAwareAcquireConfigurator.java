package app.acmelabs.taskbridge.server.acquire;

import org.flowable.common.engine.impl.ServiceConfigurator;
import org.flowable.job.service.JobServiceConfiguration;

/**
 * Installs {@link ExclusiveAwareExternalWorkerJobDataManager} on the process engine's job service. Runs in
 * {@code beforeInit}, so Flowable keeps it instead of creating its default data manager.
 */
public class ExclusiveAwareAcquireConfigurator implements ServiceConfigurator<JobServiceConfiguration> {

    private final int lookahead;

    public ExclusiveAwareAcquireConfigurator(int lookahead) {
        this.lookahead = lookahead;
    }

    @Override
    public void beforeInit(JobServiceConfiguration service) {
        service.setExternalWorkerJobDataManager(new ExclusiveAwareExternalWorkerJobDataManager(service, lookahead));
    }

    @Override
    public void afterInit(JobServiceConfiguration service) {
    }
}
