package app.acmelabs.taskbridge.client.result

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import spock.lang.Specification

class ExternalWorkerResultBuilderSpec extends Specification {

    ExternalWorkerResultBuilder builder = new ExternalWorkerResultBuilder(new ObjectMapper())

    // -------------------------------------------------------------------------
    // Success
    // -------------------------------------------------------------------------

    def "success() with no variables builds WorkerSuccess with empty map"() {
        when:
        def result = builder.success().build()

        then:
        result instanceof WorkerSuccess
        result.variables.isEmpty()
    }

    def "success() with a single variable"() {
        when:
        def result = builder.success()
                .variable("key", "value")
                .build()

        then:
        result.variables == [key: "value"]
    }

    def "success() with multiple variables via variable()"() {
        when:
        def result = builder.success()
                .variable("a", 1)
                .variable("b", 2)
                .build()

        then:
        result.variables == [a: 1, b: 2]
    }

    def "success() with variables(map) copies all entries"() {
        when:
        def result = builder.success()
                .variables([x: "hello", y: 42])
                .build()

        then:
        result.variables == [x: "hello", y: 42]
    }

    def "success() variable() and variables() can be chained"() {
        when:
        def result = builder.success()
                .variable("first", true)
                .variables([second: "two", third: 3])
                .variable("fourth", 4.0)
                .build()

        then:
        result.variables == [first: true, second: "two", third: 3, fourth: 4.0]
    }

    def "success() preserves insertion order"() {
        when:
        def result = builder.success()
                .variable("z", 1)
                .variable("a", 2)
                .variable("m", 3)
                .build()

        then:
        result.variables.keySet().toList() == ["z", "a", "m"]
    }

    def "each call to success() produces an independent builder"() {
        when:
        def r1 = builder.success().variable("k", 1).build()
        def r2 = builder.success().variable("k", 2).build()

        then:
        r1.variables == [k: 1]
        r2.variables == [k: 2]
    }

    def "convertAndAddJsonVariable serialises a POJO as a JsonNode"() {
        given:
        def pojo = [name: "Alice", age: 30]

        when:
        def result = builder.success()
                .convertAndAddJsonVariable("payload", pojo)
                .build()

        then:
        result.variables["payload"] instanceof ObjectNode
        (result.variables["payload"] as ObjectNode).get("name").asText() == "Alice"
        (result.variables["payload"] as ObjectNode).get("age").asInt() == 30
    }

    // -------------------------------------------------------------------------
    // Failure
    // -------------------------------------------------------------------------

    def "failure() defaults: null message, null details, retries=-1"() {
        when:
        def result = builder.failure().build()

        then:
        result instanceof WorkerFailure
        result.errorMessage == null
        result.errorDetails == null
        result.retries == -1
    }

    def "failure() with message only"() {
        when:
        def result = builder.failure()
                .message("something went wrong")
                .build()

        then:
        result.errorMessage == "something went wrong"
        result.errorDetails == null
        result.retries == -1
    }

    def "failure() with all fields"() {
        when:
        def result = builder.failure()
                .message("error occurred")
                .details("stack trace here")
                .retries(2)
                .build()

        then:
        result.errorMessage == "error occurred"
        result.errorDetails == "stack trace here"
        result.retries == 2
    }

    def "failure() retries(0) is preserved"() {
        when:
        def result = builder.failure().message("final failure").retries(0).build()

        then:
        result.retries == 0
    }

    def "failure() error(Throwable) populates message and details from the exception"() {
        given:
        def cause = new IllegalStateException("boom")

        when:
        def result = builder.failure().error(cause).build()

        then:
        result.errorMessage == "boom"
        result.errorDetails.contains("IllegalStateException")
        result.retries == -1
    }

    def "failure() error(null) is a no-op"() {
        when:
        def result = builder.failure().error(null).build()

        then:
        result.errorMessage == null
        result.errorDetails == null
    }

    // -------------------------------------------------------------------------
    // BPMN error
    // -------------------------------------------------------------------------

    def "bpmnError() sets errorCode"() {
        when:
        def result = builder.bpmnError("VALIDATION_ERROR").build()

        then:
        result instanceof WorkerBpmnError
        result.errorCode == "VALIDATION_ERROR"
    }

    def "bpmnError() with different codes produces distinct results"() {
        when:
        def r1 = builder.bpmnError("CODE_A").build()
        def r2 = builder.bpmnError("CODE_B").build()

        then:
        r1.errorCode == "CODE_A"
        r2.errorCode == "CODE_B"
    }
}
