package app.acmelabs.taskbridge.server;

import app.acmelabs.taskbridge.server.dto.JobBpmnErrorRequest;
import app.acmelabs.taskbridge.server.dto.JobCompletionRequest;
import app.acmelabs.taskbridge.server.dto.JobFailureRequest;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.ManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/taskbridge/jobs")
public class ExternalJobLifecycleController {

    private static final Logger log = LoggerFactory.getLogger(ExternalJobLifecycleController.class);

    private final ManagementService managementService;

    public ExternalJobLifecycleController(ManagementService managementService) {
        this.managementService = managementService;
    }

    @PostMapping("/{jobId}/complete")
    public ResponseEntity<Void> complete(@PathVariable String jobId,
                                         @RequestBody JobCompletionRequest request) {
        try {
            managementService.createExternalWorkerCompletionBuilder(jobId, request.workerId())
                    .variables(request.variables())
                    .complete();
            return ResponseEntity.ok().build();
        } catch (FlowableObjectNotFoundException e) {
            log.warn("Complete failed — job not found: jobId={}", jobId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Complete failed: jobId={}", jobId, e);
            return ResponseEntity.status(409).build();
        }
    }

    @PostMapping("/{jobId}/fail")
    public ResponseEntity<Void> fail(@PathVariable String jobId,
                                     @RequestBody JobFailureRequest request) {
        try {
            managementService.createExternalWorkerJobFailureBuilder(jobId, request.workerId())
                    .errorMessage(request.errorMessage())
                    .errorDetails(request.errorDetails())
                    .retries(request.retries())
                    .fail();
            return ResponseEntity.ok().build();
        } catch (FlowableObjectNotFoundException e) {
            log.warn("Fail failed — job not found: jobId={}", jobId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Fail failed: jobId={}", jobId, e);
            return ResponseEntity.status(409).build();
        }
    }

    @PostMapping("/{jobId}/bpmn-error")
    public ResponseEntity<Void> bpmnError(@PathVariable String jobId,
                                           @RequestBody JobBpmnErrorRequest request) {
        try {
            managementService.createExternalWorkerCompletionBuilder(jobId, request.workerId())
                    .bpmnError(request.errorCode());
            return ResponseEntity.ok().build();
        } catch (FlowableObjectNotFoundException e) {
            log.warn("BpmnError failed — job not found: jobId={}", jobId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("BpmnError failed: jobId={}", jobId, e);
            return ResponseEntity.status(409).build();
        }
    }
}
