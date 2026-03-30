package app.acmelabs.taskbridge.server;

import app.acmelabs.taskbridge.server.config.TaskBridgeServerProperties;
import app.acmelabs.taskbridge.server.dto.AcquiredJobResponse;
import org.flowable.engine.ManagementService;
import org.flowable.job.api.AcquiredExternalWorkerJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/taskbridge/jobs")
public class ExternalJobLongPollController {

    private static final Logger log = LoggerFactory.getLogger(ExternalJobLongPollController.class);

    private final ManagementService managementService;
    private final LongPollRegistry longPollRegistry;
    private final TaskBridgeServerProperties properties;

    public ExternalJobLongPollController(ManagementService managementService,
                                         LongPollRegistry longPollRegistry,
                                         TaskBridgeServerProperties properties) {
        this.managementService = managementService;
        this.longPollRegistry = longPollRegistry;
        this.properties = properties;
    }

    @GetMapping("/poll")
    public DeferredResult<List<AcquiredJobResponse>> poll(
            @RequestParam String topic,
            @RequestParam String workerId,
            @RequestParam(required = false) Integer maxJobs,
            @RequestParam(required = false) Long timeoutMs,
            @RequestParam(required = false) String lockDuration) {

        int effectiveMaxJobs = maxJobs != null ? maxJobs : properties.getMaxJobsPerPoll();
        long effectiveTimeoutMs = timeoutMs != null ? timeoutMs : properties.getDefaultTimeoutMs();
        String effectiveLockDuration = lockDuration != null ? lockDuration : properties.getDefaultLockDuration();

        DeferredResult<List<AcquiredJobResponse>> deferred =
                new DeferredResult<>(effectiveTimeoutMs, Collections.emptyList());

        // a. Try immediate fetch first
        List<AcquiredExternalWorkerJob> jobs = managementService
                .createExternalWorkerJobAcquireBuilder()
                .topic(topic, Duration.parse(effectiveLockDuration))
                .acquireAndLock(effectiveMaxJobs, workerId);

        // b. Jobs available — return immediately
        if (!jobs.isEmpty()) {
            log.debug("Immediate fetch found {} jobs for topic={}", jobs.size(), topic);
            deferred.setResult(toResponse(jobs));
            return deferred;
        }

        // c. No jobs — park the request
        longPollRegistry.park(topic, deferred);

        // d. RACE FIX: re-fetch after parking to close the window between
        //    "no jobs found" and "parked" where a job could have been created
        List<AcquiredExternalWorkerJob> raceCheckJobs = managementService
                .createExternalWorkerJobAcquireBuilder()
                .topic(topic, Duration.parse(effectiveLockDuration))
                .acquireAndLock(effectiveMaxJobs, workerId);

        if (!raceCheckJobs.isEmpty()) {
            log.debug("Race-check fetch found {} jobs for topic={}", raceCheckJobs.size(), topic);
            deferred.setResult(toResponse(raceCheckJobs));
        }

        return deferred;
    }

    private List<AcquiredJobResponse> toResponse(List<AcquiredExternalWorkerJob> jobs) {
        return jobs.stream()
                .map(job -> new AcquiredJobResponse(
                        job.getId(),
                        job.getElementId(),
                        job.getElementName(),
                        job.getProcessInstanceId(),
                        job.getProcessDefinitionId(),
                        job.getTenantId(),
                        job.getVariables(),
                        job.getRetries()))
                .toList();
    }
}
