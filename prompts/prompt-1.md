# `taskbridge` — Long-Poll External Worker Library for Flowable

**Repository:** `github.com/acmelabs/taskbridge`
**Package:** `app.acmelabs.taskbridge`
**GroupId:** `app.acmelabs.taskbridge`
**License:** MIT

---

## Library Overview

A Spring Boot starter library that provides annotation-driven external worker support with **long-polling** instead of short-polling. Designed for use with workflow engine services wrapping Flowable OSS 7.x.

**Two modules:**
- `taskbridge-server` — Embedded in the workflow engine service. Provides the long-poll endpoint, `DeferredResult` registry, and `FlowableEventListener` that wakes parked requests on job creation.
- `taskbridge-client-spring-boot-starter` — Used by worker services. Provides `@ExternalWorker` annotation, `BeanPostProcessor`-based auto-discovery, WebClient-based long-poll loop, and job lifecycle management (complete/fail/bpmn-error).

**Key design decisions:**
- Uses `WebClient` internally so it works in both MVC and WebFlux apps (no thread-per-topic).
- Worker annotation: `@ExternalWorker` — clean, framework-agnostic name.
- Server-side uses `DeferredResult` for long-poll parking (works on servlet stack without WebFlux).
- Transaction-safe: listener fires on `COMMITTED` so jobs are visible when workers fetch.
- Missed-wake-up protection: double-check after parking.
- Configuration via `taskbridge.*` YAML prefix.

---

## Module Structure

```
taskbridge/
├── taskbridge-server/
│   ├── src/main/java/app/acmelabs/taskbridge/server/
│   │   ├── LongPollRegistry.java
│   │   ├── ExternalJobCreatedListener.java
│   │   ├── ExternalJobLongPollController.java
│   │   ├── ExternalJobLifecycleController.java
│   │   ├── dto/
│   │   │   ├── AcquiredJobResponse.java
│   │   │   ├── JobCompletionRequest.java
│   │   │   ├── JobFailureRequest.java
│   │   │   └── JobBpmnErrorRequest.java
│   │   └── config/
│   │       ├── TaskBridgeServerProperties.java
│   │       └── TaskBridgeServerAutoConfiguration.java
│   └── src/main/resources/
│       └── META-INF/spring/
│           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
├── taskbridge-client-spring-boot-starter/
│   ├── src/main/java/app/acmelabs/taskbridge/client/
│   │   ├── annotation/
│   │   │   ├── ExternalWorker.java
│   │   │   ├── ExternalWorkers.java
│   │   │   └── EnableExternalWorkers.java
│   │   ├── registry/
│   │   │   ├── WorkerEndpoint.java
│   │   │   ├── WorkerEndpointRegistry.java
│   │   │   └── ExternalWorkerBeanPostProcessor.java
│   │   ├── invoker/
│   │   │   ├── WorkerMethodInvoker.java
│   │   │   └── WorkerResultAdapter.java
│   │   ├── client/
│   │   │   ├── LongPollClient.java
│   │   │   ├── JobLifecycleClient.java
│   │   │   └── LongPollLoop.java
│   │   ├── result/
│   │   │   ├── ExternalWorkerResult.java       (sealed interface)
│   │   │   ├── WorkerSuccess.java
│   │   │   ├── WorkerFailure.java
│   │   │   ├── WorkerBpmnError.java
│   │   │   └── ExternalWorkerResultBuilder.java
│   │   ├── dto/
│   │   │   └── AcquiredJob.java
│   │   └── config/
│   │       ├── TaskBridgeClientProperties.java
│   │       └── TaskBridgeClientAutoConfiguration.java
│   └── src/main/resources/
│       └── META-INF/spring/
│           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
├── LICENSE
├── README.md
└── pom.xml (parent)
```

---

## Prompt 1 of 10 — Repository scaffolding, parent POM, and open-source files

```
Create the Maven multi-module project structure for `taskbridge`.

### Repository root files

1. LICENSE — MIT License, copyright "2026 Acme Labs"

2. README.md — placeholder with:
   - Project name: taskbridge
   - One-liner: "Long-poll external worker library for Flowable"
   - Badges: placeholder for Maven Central, build status
   - "Documentation coming soon" note
   - License: MIT

3. .gitignore — standard Java/Maven gitignore (target/, *.class, .idea/, *.iml, .DS_Store)

### Parent POM

- groupId: app.acmelabs.taskbridge
- artifactId: taskbridge
- version: 0.1.0-SNAPSHOT
- packaging: pom
- name: TaskBridge
- description: Long-poll external worker library for Flowable
- url: https://github.com/acmelabs/taskbridge
- modules: taskbridge-server, taskbridge-client-spring-boot-starter

- Parent: spring-boot-starter-parent 3.2.x (match whatever version hc-workflow-engine uses)
- Properties: java 17, flowable.version = 7.2.0

- SCM section pointing to github.com/acmelabs/taskbridge
- License section: MIT
- Developer section: Acme Labs

- dependencyManagement:
  - spring-boot-dependencies BOM
  - flowable-engine ${flowable.version}

### taskbridge-server POM

- artifactId: taskbridge-server
- name: TaskBridge Server
- Dependencies:
  - spring-boot-starter-web (provided)
  - flowable-engine (provided)
  - lombok (provided)
  - jackson-databind (provided)
  - spring-boot-starter-test (test)

### taskbridge-client-spring-boot-starter POM

- artifactId: taskbridge-client-spring-boot-starter
- name: TaskBridge Client Spring Boot Starter
- Dependencies:
  - spring-boot-starter (provided)
  - spring-boot-starter-webflux (compile — for WebClient, does NOT force reactive stack)
  - lombok (provided)
  - jackson-databind (provided)
  - spring-boot-starter-test (test)
  - spring-boot-configuration-processor (optional, annotation processor)

Create empty package directories for both modules matching the structure in the overview.
Create a placeholder class in each module so it compiles:
- Server: app.acmelabs.taskbridge.server.LongPollRegistry (empty class)
- Client: app.acmelabs.taskbridge.client.annotation.ExternalWorker (empty annotation)

Verify the project compiles with `mvn compile`.
```

---

## Prompt 2 of 10 — Server DTOs and properties

```
In the taskbridge-server module, create the following classes:

1. `app.acmelabs.taskbridge.server.dto.AcquiredJobResponse` — a Java record:
   - String jobId
   - String elementId
   - String elementName
   - String processInstanceId
   - String processDefinitionId
   - String tenantId
   - Map<String, Object> variables
   - int retries

2. `app.acmelabs.taskbridge.server.dto.JobCompletionRequest` — a Java record:
   - String workerId
   - Map<String, Object> variables (nullable)

3. `app.acmelabs.taskbridge.server.dto.JobFailureRequest` — a Java record:
   - String workerId
   - String errorMessage
   - String errorDetails (nullable)
   - int retries

4. `app.acmelabs.taskbridge.server.dto.JobBpmnErrorRequest` — a Java record:
   - String workerId
   - String errorCode
   - String errorMessage (nullable)

5. `app.acmelabs.taskbridge.server.config.TaskBridgeServerProperties`
   — @ConfigurationProperties(prefix = "taskbridge.server")

   Fields:
   - long defaultTimeoutMs = 30000 (how long to park a request before returning empty)
   - String defaultLockDuration = "PT5M"
   - int maxJobsPerPoll = 5
   - boolean enabled = true

Use Lombok where appropriate (@Data on properties, records don't need it).
Add @ConstructorBinding if needed for the Spring Boot version.
Verify compilation.
```

---

## Prompt 3 of 10 — LongPollRegistry

```
In the taskbridge-server module, implement `app.acmelabs.taskbridge.server.LongPollRegistry`:

This component manages parked long-poll requests per topic.

Fields:
- ConcurrentHashMap<String, ConcurrentLinkedQueue<DeferredResult<List<AcquiredJobResponse>>>> waiters

Methods:

1. `void park(String topic, DeferredResult<List<AcquiredJobResponse>> deferred)`
   - Add the deferred to the queue for the given topic (computeIfAbsent)
   - Register onTimeout callback: remove from queue, set result to empty list
   - Register onCompletion callback: remove from queue

2. `boolean wakeUp(String topic)`
   - Get the queue for the topic
   - Poll one DeferredResult from the queue
   - If found, set its result to Collections.emptyList() (this is a "go fetch" signal)
   - Return true if a waiter was woken, false otherwise

3. `void wakeUpAll(String topic)`
   - Wake ALL parked waiters for a topic (used when multiple jobs arrive at once)
   - Poll and setResult in a loop until the queue is empty

4. `int parkedCount(String topic)`
   - Return the number of parked requests for a topic (for monitoring/logging)

Private:
5. `void remove(String topic, DeferredResult<?> deferred)`
   - Remove a specific deferred from the topic queue
   - Clean up empty queues from the map

Add @Slf4j.
Add debug logging for park, wakeUp, timeout events.
Do NOT add @Component — this bean will be registered via auto-configuration.
Verify compilation.
```

---

## Prompt 4 of 10 — ExternalJobCreatedListener

```
In the taskbridge-server module, implement `app.acmelabs.taskbridge.server.ExternalJobCreatedListener`:

This is a FlowableEventListener that fires AFTER COMMIT when an external worker job is created,
and wakes parked long-poll requests via LongPollRegistry.

Constructor injection:
- LongPollRegistry longPollRegistry

Implement FlowableEventListener:

1. `onEvent(FlowableEvent event)`:
   - Guard: return if event type is not FlowableEngineEventType.JOB_CREATED
   - Guard: return if event is not instanceof FlowableEntityEvent
   - Guard: return if entity is not instanceof ExternalWorkerJobEntity
   - Cast to ExternalWorkerJobEntity, extract topic
   - Call longPollRegistry.wakeUp(topic)
   - Log at info level: "External worker job created: jobId={}, topic={}, waking parked poll"

2. `isFailOnException()`: return false — never block the engine

3. `isFireOnTransactionLifecycleEvent()`: return true — fire after TX commit

4. `getOnTransaction()`: return "COMMITTED" — job is visible to workers only after commit

Add @Slf4j. Do NOT add @Component — registered via auto-configuration.

The exact import for the entity is:
org.flowable.job.service.impl.persistence.entity.ExternalWorkerJobEntity

Verify compilation.
```

---

## Prompt 5 of 10 — Long-poll controller and lifecycle controller

```
In the taskbridge-server module, implement two controllers:

### 1. ExternalJobLongPollController

@RestController @RequestMapping("/api/taskbridge/jobs")

Constructor injection: ManagementService, LongPollRegistry, TaskBridgeServerProperties

Endpoint: GET /api/taskbridge/jobs/poll
Parameters:
  - @RequestParam String topic
  - @RequestParam String workerId
  - @RequestParam(required = false) Integer maxJobs  — defaults to properties.maxJobsPerPoll
  - @RequestParam(required = false) Long timeoutMs   — defaults to properties.defaultTimeoutMs
  - @RequestParam(required = false) String lockDuration — defaults to properties.defaultLockDuration

Logic:
  a. Create DeferredResult<List<AcquiredJobResponse>> with effective timeout, default result = empty list
  b. Try immediate fetch: managementService.createExternalWorkerJobAcquireBuilder()
     .topic(topic, Duration.parse(effectiveLockDuration))
     .acquireAndLock(effectiveMaxJobs, workerId)
  c. If jobs found → setResult(toResponse(jobs)), return deferred
  d. If no jobs → longPollRegistry.park(topic, deferred)
  e. RACE FIX: After parking, re-fetch. If jobs found and deferred.setResult succeeds, return.
     This closes the window between "no jobs found" and "parked" where a job could be created.
  f. Return deferred

Private helper: List<AcquiredJobResponse> toResponse(List<AcquiredExternalWorkerJob> jobs)
  Map each job to AcquiredJobResponse record using:
  - job.getId(), job.getElementId(), job.getElementName()
  - job.getProcessInstanceId(), job.getProcessDefinitionId()
  - job.getTenantId(), job.getVariables(), job.getRetries()

### 2. ExternalJobLifecycleController

@RestController @RequestMapping("/api/taskbridge/jobs")

Constructor injection: ManagementService

Endpoint: POST /api/taskbridge/jobs/{jobId}/complete
  @RequestBody JobCompletionRequest
  Logic: managementService.createExternalWorkerCompletionBuilder(jobId, request.workerId())
    .variables(request.variables())
    .complete()
  Return: 200 OK

Endpoint: POST /api/taskbridge/jobs/{jobId}/fail
  @RequestBody JobFailureRequest
  Logic: managementService.createExternalWorkerJobFailureBuilder(jobId, request.workerId())
    .errorMessage(request.errorMessage())
    .errorDetails(request.errorDetails())
    .retries(request.retries())
    .fail()
  Return: 200 OK

Endpoint: POST /api/taskbridge/jobs/{jobId}/bpmn-error
  @RequestBody JobBpmnErrorRequest
  Logic: Check if Flowable OSS 7.2.0 supports BPMN error on external workers.
    If not supported, implement as fail() with errorCode in the message and
    add a TODO comment documenting the limitation.
  Return: 200 OK

Do NOT add @Component — registered via auto-configuration.
Add @Slf4j and appropriate error handling (try-catch, return 404 if job not found,
return 409 if already completed, etc.).

Verify compilation.
```

---

## Prompt 6 of 10 — Server auto-configuration

```
In the taskbridge-server module, implement the auto-configuration:

### TaskBridgeServerAutoConfiguration

Package: app.acmelabs.taskbridge.server.config

@AutoConfiguration
@ConditionalOnProperty(prefix = "taskbridge.server", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TaskBridgeServerProperties.class)

This class registers all server beans explicitly (no component scanning):

1. @Bean @ConditionalOnMissingBean
   LongPollRegistry taskBridgeLongPollRegistry()

2. @Bean @ConditionalOnMissingBean
   ExternalJobCreatedListener taskBridgeExternalJobCreatedListener(LongPollRegistry registry)

3. @Bean
   EngineConfigurationConfigurer<SpringProcessEngineConfiguration> taskBridgeListenerConfigurer(
       ExternalJobCreatedListener listener)
   — return config -> {
       List<FlowableEventListener> existing = config.getEventListeners();
       List<FlowableEventListener> merged = new ArrayList<>();
       if (existing != null) merged.addAll(existing);
       merged.add(listener);
       config.setEventListeners(merged);
     }
   — This APPENDS to existing listeners rather than replacing them.

4. @Bean @ConditionalOnMissingBean
   ExternalJobLongPollController taskBridgeLongPollController(
       ManagementService managementService,
       LongPollRegistry registry,
       TaskBridgeServerProperties properties)

5. @Bean @ConditionalOnMissingBean
   ExternalJobLifecycleController taskBridgeLifecycleController(
       ManagementService managementService)

### Spring Boot auto-configuration registration

Create file: src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
Content: app.acmelabs.taskbridge.server.config.TaskBridgeServerAutoConfiguration

Verify compilation.
```

---

## Prompt 7 of 10 — Client annotation and result types

```
In the taskbridge-client-spring-boot-starter module, create the annotation
and result types:

### 1. @ExternalWorker annotation

Package: app.acmelabs.taskbridge.client.annotation

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(ExternalWorkers.class)

Attributes:
  - String id() default ""             — unique identifier, auto-generated if empty
  - String topic()                     — REQUIRED, the topic to subscribe to
  - String lockDuration() default ""   — override global default, ISO-8601 duration (e.g. "PT5M")
  - int maxJobs() default -1           — override global default, -1 = use global
  - int concurrency() default -1       — max concurrent job processing for this topic, -1 = use global
  - long pollTimeoutMs() default -1    — override global long-poll timeout, -1 = use global

### 2. @ExternalWorkers (container annotation)

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
ExternalWorker[] value();

### 3. @EnableExternalWorkers

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(TaskBridgeClientAutoConfiguration.class)

### 4. Result types (sealed interface hierarchy)

Package: app.acmelabs.taskbridge.client.result

public sealed interface ExternalWorkerResult
    permits WorkerSuccess, WorkerFailure, WorkerBpmnError {}

public record WorkerSuccess(Map<String, Object> variables) implements ExternalWorkerResult {
    public WorkerSuccess() { this(Collections.emptyMap()); }
}

public record WorkerFailure(String errorMessage, String errorDetails, int retries)
    implements ExternalWorkerResult {
    public WorkerFailure(String errorMessage) { this(errorMessage, null, -1); }
}

public record WorkerBpmnError(String errorCode, String errorMessage) implements ExternalWorkerResult {}

### 5. ExternalWorkerResultBuilder

Fluent builder that worker methods receive as a parameter:

public class ExternalWorkerResultBuilder {
    public SuccessBuilder success() { return new SuccessBuilder(); }
    public FailureBuilder failure() { return new FailureBuilder(); }
    public BpmnErrorBuilder bpmnError(String errorCode) { return new BpmnErrorBuilder(errorCode); }

    public static class SuccessBuilder {
        private final Map<String, Object> variables = new LinkedHashMap<>();
        public SuccessBuilder variable(String name, Object value) { variables.put(name, value); return this; }
        public SuccessBuilder variables(Map<String, Object> vars) { variables.putAll(vars); return this; }
        public WorkerSuccess build() { return new WorkerSuccess(variables); }
    }

    public static class FailureBuilder {
        private String message;
        private String details;
        private int retries = -1; // -1 means use job's current retries - 1
        public FailureBuilder message(String msg) { this.message = msg; return this; }
        public FailureBuilder details(String det) { this.details = det; return this; }
        public FailureBuilder retries(int r) { this.retries = r; return this; }
        public WorkerFailure build() { return new WorkerFailure(message, details, retries); }
    }

    public static class BpmnErrorBuilder {
        private final String errorCode;
        private String message;
        public BpmnErrorBuilder(String errorCode) { this.errorCode = errorCode; }
        public BpmnErrorBuilder message(String msg) { this.message = msg; return this; }
        public WorkerBpmnError build() { return new WorkerBpmnError(errorCode, message); }
    }
}

### 6. AcquiredJob DTO

Package: app.acmelabs.taskbridge.client.dto

public record AcquiredJob(
    String jobId,
    String elementId,
    String elementName,
    String processInstanceId,
    String processDefinitionId,
    String tenantId,
    Map<String, Object> variables,
    int retries
) {}

Verify compilation.
```

---

## Prompt 8 of 10 — Client properties and HTTP clients

```
In the taskbridge-client-spring-boot-starter module, create the configuration
and HTTP client classes:

### 1. TaskBridgeClientProperties

Package: app.acmelabs.taskbridge.client.config

@ConfigurationProperties(prefix = "taskbridge.client")

Fields:
  - String baseUrl                        — REQUIRED, e.g. "http://localhost:8080"
  - String workerId = "worker-" + UUID    — auto-generated default
  - long pollTimeoutMs = 30000            — long-poll timeout in ms
  - String lockDuration = "PT5M"          — ISO-8601 duration
  - int maxJobs = 5                       — jobs per poll
  - int concurrency = 4                   — default concurrent processing threads per topic
  - int maxRetryAttempts = 3              — retries on connection errors before backing off
  - long retryBackoffMs = 5000            — backoff after connection error
  - long maxRetryBackoffMs = 30000        — max backoff cap

### 2. LongPollClient

Package: app.acmelabs.taskbridge.client.client

Uses WebClient to call the long-poll endpoint.

Constructor: WebClient webClient, TaskBridgeClientProperties properties

Method: `Flux<AcquiredJob> poll(String topic, String lockDuration, int maxJobs, long timeoutMs)`
  - Calls GET {baseUrl}/api/taskbridge/jobs/poll with query params:
    topic, workerId (from properties), maxJobs, timeoutMs, lockDuration
  - Uses .retrieve().bodyToFlux(AcquiredJob.class)
  - Applies timeout: Duration.ofMillis(timeoutMs + 5000) — 5s grace over server timeout
  - On empty response (timeout/no jobs): returns Flux.empty()
  - On error: logs and propagates for retry handling upstream

### 3. JobLifecycleClient

Package: app.acmelabs.taskbridge.client.client

Uses WebClient to call complete/fail/bpmn-error endpoints.

Constructor: WebClient webClient, TaskBridgeClientProperties properties

Methods:
  - `Mono<Void> complete(String jobId, Map<String, Object> variables)`
    POST {baseUrl}/api/taskbridge/jobs/{jobId}/complete
    Body: { "workerId": properties.workerId, "variables": variables }

  - `Mono<Void> fail(String jobId, String errorMessage, String errorDetails, int retries)`
    POST {baseUrl}/api/taskbridge/jobs/{jobId}/fail
    Body: { "workerId": properties.workerId, "errorMessage": ..., "errorDetails": ..., "retries": ... }

  - `Mono<Void> bpmnError(String jobId, String errorCode, String errorMessage)`
    POST {baseUrl}/api/taskbridge/jobs/{jobId}/bpmn-error
    Body: { "workerId": properties.workerId, "errorCode": ..., "errorMessage": ... }

All methods log at debug level and handle error responses with appropriate logging.

Verify compilation.
```

---

## Prompt 9 of 10 — BeanPostProcessor, method invoker, and poll loop

```
In the taskbridge-client-spring-boot-starter module, create the core
runtime components:

### 1. WorkerEndpoint

Package: app.acmelabs.taskbridge.client.registry

A class holding the resolved metadata for one @ExternalWorker method:

Fields:
  - String id                    — resolved from annotation or auto-generated UUID
  - String topic
  - String lockDuration          — resolved: annotation > properties > default
  - int maxJobs                  — resolved: annotation > properties > default
  - int concurrency              — resolved: annotation > properties > default
  - long pollTimeoutMs           — resolved: annotation > properties > default
  - Object bean                  — the Spring bean instance
  - Method method                — the annotated method

Make it a simple class with a builder or all-args constructor. Add getters.

### 2. WorkerMethodInvoker

Package: app.acmelabs.taskbridge.client.invoker

Responsible for invoking a worker method with the correct arguments.

Method: `ExternalWorkerResult invoke(WorkerEndpoint endpoint, AcquiredJob job)`

Logic:
  - Inspect the method's parameters by type:
    - AcquiredJob → pass the job
    - ExternalWorkerResultBuilder → pass a new builder instance
    - Map (raw or Map<String, Object>) → pass job.variables()
    - Otherwise throw IllegalStateException with descriptive error
  - Invoke via reflection (method.setAccessible(true); method.invoke(bean, args))
  - Handle return value:
    - null or void → return new WorkerSuccess() (auto-complete)
    - ExternalWorkerResult → return it directly
    - Map → return new WorkerSuccess(castToMap(returnValue))
    - Otherwise throw IllegalStateException

### 3. ExternalWorkerBeanPostProcessor

Package: app.acmelabs.taskbridge.client.registry

Implements BeanPostProcessor, SmartInitializingSingleton

Fields:
  - List<WorkerEndpoint> discoveredEndpoints = new ArrayList<>()
  - TaskBridgeClientProperties properties
  - WorkerEndpointRegistry registry

postProcessAfterInitialization(Object bean, String beanName):
  - Get the target class (unwrap proxies via AopUtils.getTargetClass if available,
    otherwise bean.getClass())
  - Scan for methods annotated with @ExternalWorker (check declared methods)
  - For each found:
    - Validate: method must be public
    - Validate: parameters are supported types (AcquiredJob, ExternalWorkerResultBuilder, Map)
    - Resolve effective values (annotation overrides > properties defaults):
      - lockDuration: annotation value if not blank, else properties.lockDuration
      - maxJobs: annotation value if > 0, else properties.maxJobs
      - concurrency: annotation value if > 0, else properties.concurrency
      - pollTimeoutMs: annotation value if > 0, else properties.pollTimeoutMs
      - id: annotation value if not blank, else "worker-" + topic + "-" + UUID.randomUUID()
    - Build WorkerEndpoint and add to discoveredEndpoints
  - Return bean unchanged

afterSingletonsInstantiated():
  - For each discovered endpoint, call registry.register(endpoint)
  - Log at info: "TaskBridge: discovered {} external worker endpoints for topics: {}"

### 4. WorkerEndpointRegistry

Package: app.acmelabs.taskbridge.client.registry

Holds all registered endpoints and manages long-poll loops.

Fields:
  - List<WorkerEndpoint> endpoints = new CopyOnWriteArrayList<>()
  - LongPollClient longPollClient
  - JobLifecycleClient lifecycleClient
  - WorkerMethodInvoker invoker
  - TaskBridgeClientProperties properties
  - Map<String, Disposable> activeLoops = new ConcurrentHashMap<>()

Methods:

register(WorkerEndpoint endpoint):
  - Add to endpoints list

startAll():
  - For each endpoint, create a LongPollLoop and call start()
  - Store the returned Disposable in activeLoops keyed by endpoint.id
  - Log at info: "TaskBridge: started poll loop for topic={}"

stop():
  - Dispose all active loops
  - Clear activeLoops map
  - Log at info: "TaskBridge: stopped all poll loops"

### 5. LongPollLoop

Package: app.acmelabs.taskbridge.client.client

The core poll loop for a single topic. Uses Project Reactor for non-blocking operation.

Constructor: WorkerEndpoint endpoint, LongPollClient longPollClient,
             JobLifecycleClient lifecycleClient, WorkerMethodInvoker invoker,
             TaskBridgeClientProperties properties

Method: `Disposable start()`

Logic using Project Reactor:

  return Mono.defer(() ->
      longPollClient.poll(
              endpoint.getTopic(),
              endpoint.getLockDuration(),
              endpoint.getMaxJobs(),
              endpoint.getPollTimeoutMs())
          .flatMap(job ->
              Mono.fromCallable(() -> invoker.invoke(endpoint, job))
                  .subscribeOn(Schedulers.boundedElastic())
                  .flatMap(result -> handleResult(job, result))
                  .onErrorResume(ex -> {
                      log.error("TaskBridge: worker error for topic={}, jobId={}",
                          endpoint.getTopic(), job.jobId(), ex);
                      int retries = Math.max(job.retries() - 1, 0);
                      return lifecycleClient.fail(job.jobId(),
                          ex.getMessage(), null, retries);
                  }),
              endpoint.getConcurrency()
          )
          .then()
  )
  .repeat()
  .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(properties.getRetryBackoffMs()))
      .maxBackoff(Duration.ofMillis(properties.getMaxRetryBackoffMs()))
      .doBeforeRetry(signal -> log.warn("TaskBridge: poll loop retry for topic={}, attempt={}",
          endpoint.getTopic(), signal.totalRetries())))
  .subscribe();

Private: `Mono<Void> handleResult(AcquiredJob job, ExternalWorkerResult result)`
  - Use pattern matching or instanceof chain:
    - WorkerSuccess s → lifecycleClient.complete(job.jobId(), s.variables())
    - WorkerFailure f → lifecycleClient.fail(job.jobId(), f.errorMessage(),
        f.errorDetails(),
        f.retries() == -1 ? Math.max(job.retries() - 1, 0) : f.retries())
    - WorkerBpmnError e → lifecycleClient.bpmnError(job.jobId(), e.errorCode(),
        e.errorMessage())

Verify compilation.
```

---

## Prompt 10 of 10 — Client auto-configuration, lifecycle, README, and final verification

```
In the taskbridge-client-spring-boot-starter module, create the
auto-configuration, lifecycle management, and finish the README:

### 1. TaskBridgeClientAutoConfiguration

Package: app.acmelabs.taskbridge.client.config

@AutoConfiguration
@EnableConfigurationProperties(TaskBridgeClientProperties.class)
@ConditionalOnProperty(prefix = "taskbridge.client", name = "base-url")

Beans:

@Bean @ConditionalOnMissingBean(name = "taskBridgeWebClient")
WebClient taskBridgeWebClient(TaskBridgeClientProperties props):
  - Build WebClient with baseUrl = props.getBaseUrl()
  - Set default Content-Type: APPLICATION_JSON
  - Configure response timeout: Duration.ofMillis(props.getPollTimeoutMs() + 10000)

@Bean @ConditionalOnMissingBean
LongPollClient taskBridgeLongPollClient(
    @Qualifier("taskBridgeWebClient") WebClient webClient,
    TaskBridgeClientProperties props)

@Bean @ConditionalOnMissingBean
JobLifecycleClient taskBridgeJobLifecycleClient(
    @Qualifier("taskBridgeWebClient") WebClient webClient,
    TaskBridgeClientProperties props)

@Bean @ConditionalOnMissingBean
WorkerMethodInvoker taskBridgeWorkerMethodInvoker()

@Bean @ConditionalOnMissingBean
WorkerEndpointRegistry taskBridgeWorkerEndpointRegistry(
    LongPollClient longPollClient,
    JobLifecycleClient lifecycleClient,
    WorkerMethodInvoker invoker,
    TaskBridgeClientProperties props)

@Bean
ExternalWorkerBeanPostProcessor taskBridgeExternalWorkerBeanPostProcessor(
    TaskBridgeClientProperties props,
    WorkerEndpointRegistry registry)

@Bean
TaskBridgeLifecycle taskBridgeLifecycle(WorkerEndpointRegistry registry)

### 2. TaskBridgeLifecycle

Package: app.acmelabs.taskbridge.client.config

Implements SmartLifecycle — starts poll loops after full context is ready.

Fields:
  - WorkerEndpointRegistry registry
  - volatile boolean running = false

start():
  - registry.startAll()
  - running = true
  - log.info("TaskBridge: external worker poll loops started")

stop():
  - registry.stop()
  - running = false
  - log.info("TaskBridge: external worker poll loops stopped")

isRunning(): return running
getPhase(): return Integer.MAX_VALUE - 100  — start late, stop early

### 3. Auto-configuration registration

Create file:
src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
Content: app.acmelabs.taskbridge.client.config.TaskBridgeClientAutoConfiguration

### 4. Update README.md

Replace the placeholder README with full documentation:

# TaskBridge

Long-poll external worker library for [Flowable](https://flowable.org).

Replaces short-polling with long-polling for external worker jobs, reducing
latency from seconds to milliseconds while eliminating wasteful polling load.

## Modules

| Module | Purpose |
|--------|---------|
| `taskbridge-server` | Embed in your Flowable workflow service |
| `taskbridge-client-spring-boot-starter` | Use in your worker services |

## Quick Start

### Server (workflow-service)

Add dependency:
```xml
<dependency>
    <groupId>app.acmelabs.taskbridge</groupId>
    <artifactId>taskbridge-server</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

No further configuration needed. The server auto-configures with sensible defaults.

Optional configuration:
```yaml
taskbridge:
  server:
    enabled: true
    default-timeout-ms: 30000
    default-lock-duration: PT5M
    max-jobs-per-poll: 5
```

### Client (worker-service)

Add dependency:
```xml
<dependency>
    <groupId>app.acmelabs.taskbridge</groupId>
    <artifactId>taskbridge-client-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Configure the connection:
```yaml
taskbridge:
  client:
    base-url: http://localhost:8080
    worker-id: my-worker-1
    poll-timeout-ms: 30000
    lock-duration: PT5M
    max-jobs: 5
    concurrency: 4
```

Define workers:
```java
@Component
public class PaymentWorker {

    @ExternalWorker(topic = "payment-processing")
    public ExternalWorkerResult process(AcquiredJob job, ExternalWorkerResultBuilder result) {
        String orderId = (String) job.variables().get("orderId");
        // ... business logic ...
        return result.success()
            .variable("paymentId", "PAY-123")
            .variable("status", "COMPLETED")
            .build();
    }
}
```

### Worker Method Signatures

Auto-complete (void return):
```java
@ExternalWorker(topic = "send-notification")
public void sendNotification(AcquiredJob job) {
    // job auto-completes on success, auto-fails on exception
}
```

Return variables as Map:
```java
@ExternalWorker(topic = "enrich-data")
public Map<String, Object> enrich(AcquiredJob job) {
    return Map.of("enriched", true, "score", 42);
}
```

Explicit result with failure handling:
```java
@ExternalWorker(topic = "validate-address")
public ExternalWorkerResult validate(AcquiredJob job, ExternalWorkerResultBuilder result) {
    try {
        Address validated = addressService.validate(job.variables());
        return result.success().variable("address", validated).build();
    } catch (ValidationException e) {
        return result.failure().message(e.getMessage()).retries(0).build();
    }
}
```

Per-topic overrides:
```java
@ExternalWorker(topic = "heavy-computation", concurrency = 2, lockDuration = "PT10M")
public void compute(AcquiredJob job) {
    // runs with max 2 concurrent jobs, 10-minute lock
}
```

## How It Works

1. Workflow engine creates an external worker job
2. `taskbridge-server` listener fires after transaction commit
3. Listener wakes a parked long-poll request for that topic
4. Worker receives the wake-up signal, fetches and locks the job
5. Worker executes the `@ExternalWorker` method
6. Worker completes or fails the job via REST

If no notification is received (network blip, restart), the long-poll times out
and the worker immediately re-polls — acting as a built-in fallback.

## Requirements

- Java 17+
- Spring Boot 3.2+
- Flowable OSS 7.x (server module only)

## License

MIT

Document the full YAML configuration reference for both server and client.

### 5. Final verification

Run `mvn compile` from the parent project root.
Fix any compilation errors.
Verify the full module structure matches the design.
```

---

## Deferred / Backlog

Items deliberately left out of this initial implementation:

| Item | Reason |
|---|---|
| Unit tests | Add after core compiles and integrates. Follow-up prompt set. |
| Integration test with embedded Flowable | Requires test infrastructure. Separate prompt set. |
| Multi-tenancy support | Add `tenantId` filtering to poll endpoint and annotation. |
| BPMN error on external workers | Flowable OSS 7.2.0 may not support natively. Document limitation. |
| Metrics / Actuator endpoint | Expose poll loop health, job counts, latency histograms. |
| Graceful shutdown with in-flight jobs | SmartLifecycle.stop() disposes loops but doesn't wait for in-flight. |
| Security (auth headers) | Add configurable Basic/Bearer auth to TaskBridgeClientProperties. |
| Health indicator | Custom HealthIndicator checking connectivity to workflow-service. |
| Variable filtering | Add `fetchVariables` to @ExternalWorker and pass to poll endpoint. |
| Maven Central publishing | GitHub Actions workflow for signing and publishing to Central. |
| Example project | Standalone example app demonstrating server + client integration. |