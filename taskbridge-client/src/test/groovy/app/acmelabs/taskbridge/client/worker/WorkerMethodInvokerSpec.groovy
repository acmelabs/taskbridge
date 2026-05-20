package app.acmelabs.taskbridge.client.worker

import app.acmelabs.taskbridge.client.dto.AcquiredJob
import app.acmelabs.taskbridge.client.result.ExternalWorkerResult
import app.acmelabs.taskbridge.client.result.ExternalWorkerResultBuilder
import app.acmelabs.taskbridge.client.result.WorkerBpmnError
import app.acmelabs.taskbridge.client.result.WorkerFailure
import app.acmelabs.taskbridge.client.result.WorkerSuccess
import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification

class WorkerMethodInvokerSpec extends Specification {

    WorkerMethodInvoker invoker = new WorkerMethodInvoker(new ObjectMapper())

    AcquiredJob job = new AcquiredJob().tap {
        id = "job-1"
        topicName = "test-topic"
        scopeId = "proc-inst-1"
        scopeType = "bpmn"
        scopeDefinitionId = "proc-def-1"
        elementId = "element-1"
        elementName = "Task"
        tenantId = "tenant-1"
        variables = [inputKey: "inputValue"]
        retries = 3
    }

    private WorkerEndpoint endpointFor(Object bean, String methodName, Class<?>... paramTypes) {
        def method = bean.getClass().getDeclaredMethod(methodName, paramTypes)
        new WorkerEndpoint("test-topic", "PT5M", 5, 4, 3, bean, method)
    }

    // =========================================================================
    // Void return
    // =========================================================================

    def "void method with no params returns WorkerSuccess with empty variables"() {
        given:
        def worker = new VoidNoArgsWorker()
        def ep = endpointFor(worker, "handle")

        when:
        def result = invoker.invoke(ep, job)

        then:
        result instanceof WorkerSuccess
        (result as WorkerSuccess).variables.isEmpty()
        worker.called
    }

    // =========================================================================
    // Parameter injection
    // =========================================================================

    def "injects AcquiredJob parameter"() {
        given:
        def worker = new AcquiredJobParamWorker()
        def ep = endpointFor(worker, "handle", AcquiredJob)

        when:
        invoker.invoke(ep, job)

        then:
        worker.receivedJob.is(job)
    }

    def "injects ExternalWorkerResultBuilder parameter"() {
        given:
        def worker = new BuilderParamWorker()
        def ep = endpointFor(worker, "handle", ExternalWorkerResultBuilder)

        when:
        invoker.invoke(ep, job)

        then:
        worker.receivedBuilder instanceof ExternalWorkerResultBuilder
    }

    def "injects job variables as Map parameter"() {
        given:
        def worker = new MapParamWorker()
        def ep = endpointFor(worker, "handle", Map)

        when:
        invoker.invoke(ep, job)

        then:
        worker.receivedMap == [inputKey: "inputValue"]
    }

    def "injects multiple parameters in declared order"() {
        given:
        def worker = new MultiParamWorker()
        def ep = endpointFor(worker, "handle", AcquiredJob, ExternalWorkerResultBuilder)

        when:
        def result = invoker.invoke(ep, job)

        then:
        result instanceof WorkerSuccess
        (result as WorkerSuccess).variables == [builtWith: "inputValue"]
    }

    def "throws IllegalStateException for unsupported parameter type"() {
        given:
        def worker = new UnsupportedParamWorker()
        def ep = endpointFor(worker, "handle", String)

        when:
        invoker.invoke(ep, job)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Unsupported parameter type")
        ex.message.contains("java.lang.String")
        ex.message.contains("handle")
    }

    // =========================================================================
    // Return type handling
    // =========================================================================

    def "ExternalWorkerResult return value is passed through unchanged"() {
        given:
        def expected = new WorkerFailure("oops", "details", 2)
        def worker = new DirectResultWorker(result: expected)
        def ep = endpointFor(worker, "handle")

        when:
        def result = invoker.invoke(ep, job)

        then:
        result.is(expected)
    }

    def "Map return value is wrapped in WorkerSuccess"() {
        given:
        def worker = new MapReturnWorker()
        def ep = endpointFor(worker, "handle")

        when:
        def result = invoker.invoke(ep, job)

        then:
        result instanceof WorkerSuccess
        (result as WorkerSuccess).variables == [output: "computed"]
    }

    def "null return from non-void method yields WorkerSuccess with empty variables"() {
        given:
        def worker = new NullReturnWorker()
        def ep = endpointFor(worker, "handle")

        when:
        def result = invoker.invoke(ep, job)

        then:
        result instanceof WorkerSuccess
        (result as WorkerSuccess).variables.isEmpty()
    }

    def "throws IllegalStateException for unsupported return type"() {
        given:
        def worker = new UnsupportedReturnWorker()
        def ep = endpointFor(worker, "handle")

        when:
        invoker.invoke(ep, job)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Unsupported return type")
        ex.message.contains("java.lang.String")
    }

    // =========================================================================
    // Exception propagation
    // =========================================================================

    def "RuntimeException thrown by worker propagates as-is"() {
        given:
        def worker = new ThrowingRuntimeWorker()
        def ep = endpointFor(worker, "handle")

        when:
        invoker.invoke(ep, job)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "bad input"
    }

    def "checked exception thrown by worker is wrapped in RuntimeException"() {
        given:
        def worker = new ThrowingCheckedWorker()
        def ep = endpointFor(worker, "handle")

        when:
        invoker.invoke(ep, job)

        then:
        def ex = thrown(RuntimeException)
        ex.message == "Worker method invocation failed"
        ex.cause.message == "checked error"
    }

    // =========================================================================
    // Builder usage
    // =========================================================================

    def "worker builds success result via ExternalWorkerResultBuilder"() {
        given:
        def worker = new BuilderSuccessWorker()
        def ep = endpointFor(worker, "handle", ExternalWorkerResultBuilder)

        when:
        def result = invoker.invoke(ep, job)

        then:
        result instanceof WorkerSuccess
        (result as WorkerSuccess).variables == [status: "done"]
    }

    def "worker builds failure result via ExternalWorkerResultBuilder"() {
        given:
        def worker = new BuilderFailureWorker()
        def ep = endpointFor(worker, "handle", ExternalWorkerResultBuilder)

        when:
        def result = invoker.invoke(ep, job)

        then:
        result instanceof WorkerFailure
        (result as WorkerFailure).errorMessage == "something went wrong"
        (result as WorkerFailure).retries == 1
    }

    def "worker builds BPMN error result via ExternalWorkerResultBuilder"() {
        given:
        def worker = new BuilderBpmnErrorWorker()
        def ep = endpointFor(worker, "handle", ExternalWorkerResultBuilder)

        when:
        def result = invoker.invoke(ep, job)

        then:
        result instanceof WorkerBpmnError
        (result as WorkerBpmnError).errorCode == "PAYMENT_FAILED"
    }

    // =========================================================================
    // Inner worker classes
    // =========================================================================

    static class VoidNoArgsWorker {
        boolean called = false
        void handle() { called = true }
    }

    static class AcquiredJobParamWorker {
        AcquiredJob receivedJob
        void handle(AcquiredJob job) { receivedJob = job }
    }

    static class BuilderParamWorker {
        ExternalWorkerResultBuilder receivedBuilder
        void handle(ExternalWorkerResultBuilder builder) { receivedBuilder = builder }
    }

    static class MapParamWorker {
        Map receivedMap
        void handle(Map vars) { receivedMap = vars }
    }

    static class MultiParamWorker {
        ExternalWorkerResult handle(AcquiredJob job, ExternalWorkerResultBuilder builder) {
            builder.success().variable("builtWith", job.variables["inputKey"]).build()
        }
    }

    static class UnsupportedParamWorker {
        void handle(String s) {}
    }

    static class DirectResultWorker {
        ExternalWorkerResult result
        ExternalWorkerResult handle() { result }
    }

    static class MapReturnWorker {
        Map handle() { [output: "computed"] }
    }

    static class NullReturnWorker {
        ExternalWorkerResult handle() { null }
    }

    static class UnsupportedReturnWorker {
        String handle() { "oops" }
    }

    static class ThrowingRuntimeWorker {
        void handle() { throw new IllegalArgumentException("bad input") }
    }

    static class ThrowingCheckedWorker {
        void handle() throws Exception { throw new Exception("checked error") }
    }

    static class BuilderSuccessWorker {
        ExternalWorkerResult handle(ExternalWorkerResultBuilder builder) {
            builder.success().variable("status", "done").build()
        }
    }

    static class BuilderFailureWorker {
        ExternalWorkerResult handle(ExternalWorkerResultBuilder builder) {
            builder.failure().message("something went wrong").retries(1).build()
        }
    }

    static class BuilderBpmnErrorWorker {
        ExternalWorkerResult handle(ExternalWorkerResultBuilder builder) {
            builder.bpmnError("PAYMENT_FAILED").build()
        }
    }
}
