package app.acmelabs.taskbridge.client.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*
import static org.springframework.test.web.client.response.MockRestResponseCreators.*

class FlowableRestClientSpec extends Specification {

    static final String BASE = "http://flowable"
    static final String ACQUIRE_URL = "$BASE/external-job-api/acquire/jobs"

    ObjectMapper objectMapper = new ObjectMapper()
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE)
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build()
    FlowableRestClient client = new FlowableRestClient(builder.build(), objectMapper)

    // =========================================================================
    // acquireJobs — request body
    // =========================================================================

    def "acquireJobs sends correct request body fields"() {
        given:
        server.expect(requestTo(ACQUIRE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json('{"topic":"payment","lockDuration":"PT5M","numberOfTasks":3,"numberOfRetries":5,"workerId":"w1"}'))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        when:
        client.acquireJobs("payment", "PT5M", 3, 5, "w1")

        then:
        server.verify()
    }

    def "acquireJobs returns empty list for empty array response"() {
        given:
        server.expect(requestTo(ACQUIRE_URL))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        expect:
        client.acquireJobs("t", "PT1M", 1, 3, "w") == []
    }

    // =========================================================================
    // acquireJobs — BPMN job parsing
    // =========================================================================

    def "acquireJobs parses BPMN job fields and sets scopeType=bpmn"() {
        given:
        server.expect(requestTo(ACQUIRE_URL))
                .andRespond(withSuccess("""[{
                    "id": "job-1",
                    "correlationId": "corr-1",
                    "retries": 3,
                    "processInstanceId": "proc-1",
                    "executionId": "exec-1",
                    "processDefinitionId": "def-1",
                    "tenantId": "tenant-1",
                    "elementId": "task-el",
                    "elementName": "Payment Task",
                    "lockOwner": "w1",
                    "lockExpirationTime": "2025-06-01T10:00:00Z",
                    "createTime": "2025-06-01T09:00:00Z",
                    "variables": []
                }]""", MediaType.APPLICATION_JSON))

        when:
        def jobs = client.acquireJobs("payment", "PT5M", 1, 3, "w1")

        then:
        jobs.size() == 1
        with(jobs[0]) {
            id == "job-1"
            correlationId == "corr-1"
            retries == 3
            scopeId == "proc-1"
            scopeType == "bpmn"
            subScopeId == "exec-1"
            scopeDefinitionId == "def-1"
            tenantId == "tenant-1"
            elementId == "task-el"
            elementName == "Payment Task"
            workerId == "w1"
            lockExpirationTime == Instant.parse("2025-06-01T10:00:00Z")
            createTime == Instant.parse("2025-06-01T09:00:00Z")
            topicName == "payment"
        }
    }

    // =========================================================================
    // acquireJobs — CMMN job parsing
    // =========================================================================

    def "acquireJobs parses CMMN job using generic scope fields"() {
        given:
        server.expect(requestTo(ACQUIRE_URL))
                .andRespond(withSuccess("""[{
                    "id": "job-2",
                    "retries": 1,
                    "scopeId": "case-1",
                    "scopeType": "cmmn",
                    "subScopeId": "plan-1",
                    "scopeDefinitionId": "casedef-1",
                    "variables": []
                }]""", MediaType.APPLICATION_JSON))

        when:
        def jobs = client.acquireJobs("review", "PT5M", 1, 3, "w1")

        then:
        with(jobs[0]) {
            scopeId == "case-1"
            scopeType == "cmmn"
            subScopeId == "plan-1"
            scopeDefinitionId == "casedef-1"
        }
    }

    // =========================================================================
    // acquireJobs — variable type parsing
    // =========================================================================

    @Unroll
    def "acquireJobs parses variable type '#type'"() {
        given:
        server.expect(requestTo(ACQUIRE_URL))
                .andRespond(withSuccess("""[{
                    "id": "job-1", "retries": 1,
                    "variables": [{"name":"v","type":"$type","value":$jsonValue}]
                }]""", MediaType.APPLICATION_JSON))

        when:
        def jobs = client.acquireJobs("t", "PT1M", 1, 3, "w")

        then:
        expectedCheck(jobs[0].variables["v"])

        where:
        type           | jsonValue                      | expectedCheck
        "string"       | '"hello"'                      | { it == "hello" }
        "integer"      | '42'                           | { it == 42 }
        "long"         | '9876543210'                   | { it == 9876543210L }
        "double"       | '3.14'                         | { Math.abs((it as double) - 3.14) < 0.001 }
        "boolean"      | 'true'                         | { it == true }
        "short"        | '7'                            | { it == (short) 7 }
        "instant"      | '"2025-01-01T00:00:00Z"'       | { it == Instant.parse("2025-01-01T00:00:00Z") }
        "localDate"    | '"2025-03-15"'                 | { it == LocalDate.of(2025, 3, 15) }
        "localDateTime"| '"2025-03-15T12:30:00"'        | { it == LocalDateTime.of(2025, 3, 15, 12, 30) }
        "json"         | '{"key":"val"}'                | { it instanceof com.fasterxml.jackson.databind.JsonNode }
        "date"         | '"2025-01-01T00:00:00Z"'       | { it instanceof Date }
    }

    def "acquireJobs assigns topicName from the topic parameter"() {
        given:
        server.expect(requestTo(ACQUIRE_URL))
                .andRespond(withSuccess('[{"id":"j","retries":1,"variables":[]}]', MediaType.APPLICATION_JSON))

        when:
        def jobs = client.acquireJobs("my-topic", "PT1M", 1, 3, "w")

        then:
        jobs[0].topicName == "my-topic"
    }

    def "acquireJobs throws on 4xx response"() {
        given:
        server.expect(requestTo(ACQUIRE_URL))
                .andRespond(withBadRequest())

        when:
        client.acquireJobs("t", "PT1M", 1, 3, "w")

        then:
        thrown(HttpClientErrorException)
    }

    def "acquireJobs throws on 5xx response"() {
        given:
        server.expect(requestTo(ACQUIRE_URL))
                .andRespond(withServerError())

        when:
        client.acquireJobs("t", "PT1M", 1, 3, "w")

        then:
        thrown(HttpServerErrorException)
    }

    // =========================================================================
    // completeJob — request body
    // =========================================================================

    def "completeJob sends POST to correct URL with workerId and variables array"() {
        given:
        server.expect(requestTo("$BASE/external-job-api/acquire/jobs/job-42/complete"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json('{"workerId":"w1","variables":[{"name":"status","type":"string","value":"done"}]}'))
                .andRespond(withNoContent())

        when:
        client.completeJob("job-42", "w1", [status: "done"])

        then:
        server.verify()
    }

    def "completeJob sends empty variables array when map is empty"() {
        given:
        server.expect(requestTo("$BASE/external-job-api/acquire/jobs/j/complete"))
                .andExpect(content().json('{"workerId":"w","variables":[]}'))
                .andRespond(withNoContent())

        when:
        client.completeJob("j", "w", [:])

        then:
        server.verify()
    }

    // =========================================================================
    // Variable serialisation: toVariableArray (tested directly, package-private)
    // =========================================================================

    @Unroll
    def "toVariableArray serialises #description as type '#expectedType'"() {
        when:
        def array = client.toVariableArray([v: value])

        then:
        array[0].get("type").asText() == expectedType
        valueCheck(array[0].get("value"))

        where:
        description     | value                                    | expectedType    | valueCheck
        "String"        | "hello"                                  | "string"        | { it.asText() == "hello" }
        "Integer"       | 42                                       | "integer"       | { it.asInt() == 42 }
        "Long"          | 99L                                      | "long"          | { it.asLong() == 99L }
        "Double"        | 1.5d                                     | "double"        | { it.asDouble() == 1.5d }
        "Boolean true"  | true                                     | "boolean"       | { it.asBoolean() }
        "Boolean false" | false                                    | "boolean"       | { !it.asBoolean() }
        "Short"         | (42 as Short)                            | "short"         | { it.shortValue() == 42 }
        "Instant"       | Instant.parse("2025-01-01T00:00:00Z")   | "instant"       | { it.asText() == "2025-01-01T00:00:00Z" }
        "LocalDate"     | LocalDate.of(2025, 3, 15)               | "localDate"     | { it.asText() == "2025-03-15" }
        "LocalDateTime" | LocalDateTime.of(2025, 3, 15, 12, 0)   | "localDateTime" | { it.asText() == "2025-03-15T12:00" }
        "JsonNode"      | new ObjectMapper().createObjectNode()   | "json"          | { !it.isNull() }
        "POJO"          | [a: 1]                                  | "json"          | { it.isObject() }
    }

    def "toVariableArray serialises Date as type 'date' with ISO instant string"() {
        given:
        def date = Date.from(Instant.parse("2025-06-01T12:00:00Z"))

        when:
        def array = client.toVariableArray([d: date])

        then:
        array[0].get("type").asText() == "date"
        array[0].get("value").asText() == "2025-06-01T12:00:00Z"
    }

    def "toVariableArray preserves variable ordering"() {
        when:
        def array = client.toVariableArray([z: "last", a: "first", m: "mid"])

        then:
        [array[0].get("name").asText(), array[1].get("name").asText(), array[2].get("name").asText()] == ["z", "a", "m"]
    }

    def "toVariableArray returns empty array for null map"() {
        expect:
        client.toVariableArray(null).isEmpty()
    }

    // =========================================================================
    // failJob — request body
    // =========================================================================

    def "failJob sends POST to correct URL with all required fields"() {
        given:
        server.expect(requestTo("$BASE/external-job-api/acquire/jobs/job-7/fail"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json('{"workerId":"w1","errorMessage":"timeout","errorDetails":"stack","retries":2}'))
                .andRespond(withNoContent())

        when:
        client.failJob("job-7", "w1", "timeout", "stack", 2)

        then:
        server.verify()
    }

    // =========================================================================
    // bpmnError — request body
    // =========================================================================

    def "bpmnError sends POST to correct URL with workerId and errorCode"() {
        given:
        server.expect(requestTo("$BASE/external-job-api/acquire/jobs/job-9/bpmnError"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json('{"workerId":"w1","errorCode":"PAYMENT_FAILED"}'))
                .andRespond(withNoContent())

        when:
        client.bpmnError("job-9", "w1", "PAYMENT_FAILED")

        then:
        server.verify()
    }

    // =========================================================================
    // Auth
    // =========================================================================

    def "sends Basic auth header when credentials are configured via interceptor"() {
        given:
        String encoded = Base64.encoder.encodeToString("admin:secret".getBytes(StandardCharsets.UTF_8))

        RestClient.Builder authBuilder = RestClient.builder()
                .baseUrl(BASE)
                .requestInterceptor { request, body, execution ->
                    request.headers.set("Authorization", "Basic " + encoded)
                    execution.execute(request, body)
                }
        MockRestServiceServer authServer = MockRestServiceServer.bindTo(authBuilder).build()
        FlowableRestClient authClient = new FlowableRestClient(authBuilder.build(), objectMapper)

        authServer.expect(requestTo(ACQUIRE_URL))
                .andExpect(header("Authorization", "Basic " + encoded))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        when:
        authClient.acquireJobs("t", "PT1M", 1, 3, "w")

        then:
        authServer.verify()
    }

    def "does not send auth header when no interceptor is configured"() {
        given:
        server.expect(requestTo(ACQUIRE_URL))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        when:
        client.acquireJobs("t", "PT1M", 1, 3, "w")

        then:
        server.verify()
    }
}
