# TaskBridge

Redis-signalled external worker library for [Flowable](https://flowable.org).


[![Maven Central](https://img.shields.io/maven-central/v/io.github.acmelabs/taskbridge.svg)](https://search.maven.org/search?q=g:io.github.acmelabs)
[![Build Status](https://img.shields.io/github/actions/workflow/status/acmelabs/taskbridge/maven.yml)](https://github.com/acmelabs/taskbridge/actions)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

An External Worker Task in BPMN or CMMN is a task where the custom logic is executed externally to Flowable, i.e. on a separate server. When the process or case engine arrives at such a task, it creates an external job exposed over the REST API. Through this REST API, the job can be acquired and locked. Once locked, the custom logic signals over REST that the work is done so the process or case can continue.

TaskBridge makes implementing such custom logic in Java easy by abstracting the low-level details of the REST API — letting you focus on actual business logic. It replaces Flowable's default short-polling with a Redis pub/sub wakeup signal, so workers are notified of new jobs within milliseconds of the transaction committing while producing zero polling traffic when there is nothing to do.

## Modules

| Module                | Purpose                                                     |
|-----------------------|-------------------------------------------------------------|
| `taskbridge-server`   | Embed in your Flowable workflow service                     |
| `taskbridge-client`   | Embed in your external worker service                       |

Both modules require [Spring Data Redis](https://spring.io/projects/spring-data-redis) — Redis is the signalling channel, not an optional add-on.

## Quick Start

### Server (workflow service)

Add dependencies:

```xml
<dependency>
    <groupId>app.acmelabs.taskbridge</groupId>
    <artifactId>taskbridge-server</artifactId>
    <version>VERSION</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

No further configuration needed. The server auto-configures with sensible defaults.

Optional configuration:

```yaml
taskbridge:
  server:
    enabled: true                    # set to false to disable (default true)
    redis-channel-prefix: taskbridge: # prefix for pub/sub channels (default taskbridge:)
```

Point your app at Redis via the standard Spring Boot properties:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### Client (worker service)

Add dependencies:

```xml
<dependency>
    <groupId>app.acmelabs.taskbridge</groupId>
    <artifactId>taskbridge-client</artifactId>
    <version>VERSION</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Configure the connection to your Flowable workflow service:

```yaml
taskbridge:
  client:
    flowable-base-url: http://localhost:8080
    username: admin
    password: test
```

Setting `flowable-base-url` is all that is required. Point Redis at the same instance as the server.

Define workers:

```java
@Component
public class PaymentWorker {

    @ExternalWorker(topic = "payment-processing")
    public ExternalWorkerResult process(AcquiredJob job, ExternalWorkerResultBuilder result) {
        String orderId = (String) job.getVariables().get("orderId");
        // ... business logic ...
        return result.success()
                .variable("paymentId", "PAY-123")
                .variable("status", "COMPLETED")
                .build();
    }
}
```

## Worker Method Signatures

Auto-complete (void return):

```java
@ExternalWorker(topic = "send-notification")
public void sendNotification(AcquiredJob job) {
    // job auto-completes on success, auto-fails on uncaught exception
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
        Address validated = addressService.validate(job.getVariables());
        return result.success().variable("address", validated).build();
    } catch (ValidationException e) {
        return result.failure().message(e.getMessage()).retries(0).build();
    }
}
```

Store a POJO as a JSON variable:

```java
@ExternalWorker(topic = "enrich-order")
public ExternalWorkerResult enrich(AcquiredJob job, ExternalWorkerResultBuilder result) {
    OrderMetadata meta = fetchMetadata(job);
    return result.success()
            .variable("score", 95)
            .convertAndAddJsonVariable("metadata", meta)
            .build();
}
```

Fail with full exception details (message + stack trace):

```java
@ExternalWorker(topic = "call-external-api")
public ExternalWorkerResult call(AcquiredJob job, ExternalWorkerResultBuilder result) {
    try {
        return result.success().variable("response", api.call()).build();
    } catch (ApiException e) {
        // errorMessage = e.getMessage(), errorDetails = full stack trace
        return result.failure().error(e).retries(2).build();
    }
}
```

Raise a BPMN error:

```java
@ExternalWorker(topic = "validate-payment")
public ExternalWorkerResult validate(AcquiredJob job, ExternalWorkerResultBuilder result) {
    if (isBlocked(job)) {
        return result.bpmnError("PAYMENT_BLOCKED").build();
    }
    return result.success().build();
}
```

Per-topic overrides:

```java
@ExternalWorker(topic = "heavy-computation", concurrency = 2, lockDuration = "PT10M")
public void compute(AcquiredJob job) {
    // runs with at most 2 concurrent executions, 10-minute lock
}
```

### Supported Parameter Types

Worker methods may declare any combination of these parameter types:

| Type                          | What is injected                                          |
|-------------------------------|-----------------------------------------------------------|
| `AcquiredJob`                 | The acquired job with all metadata and variables          |
| `ExternalWorkerResultBuilder` | Fluent builder for success / failure / BPMN error results |
| `Map` / `Map<String, Object>` | Shortcut for `job.getVariables()`                         |

### Supported Return Types

| Return type            | Behaviour                                                                       |
|------------------------|---------------------------------------------------------------------------------|
| `void`                 | Auto-completes the job; any uncaught exception auto-fails it                    |
| `ExternalWorkerResult` | Honours the returned `WorkerSuccess`, `WorkerFailure`, or `WorkerBpmnError`     |
| `Map<String, Object>`  | Auto-completes and passes the map as output variables                           |

### Output Variable Types

The `.variable(name, value)` method on `SuccessBuilder` accepts `Object`. Supported types and their Flowable storage:

| Java type               | Flowable variable type | Notes                                              |
|-------------------------|------------------------|----------------------------------------------------|
| `String`                | `string`               |                                                    |
| `Short`                 | `short`                |                                                    |
| `Integer`               | `integer`              |                                                    |
| `Long`                  | `long`                 |                                                    |
| `Double`                | `double`               |                                                    |
| `Boolean`               | `boolean`              |                                                    |
| `java.util.Date`        | `date`                 |                                                    |
| `java.time.Instant`     | `instant`              |                                                    |
| `java.time.LocalDate`   | `localDate`            |                                                    |
| `java.time.LocalDateTime` | `localDateTime`      |                                                    |
| `JsonNode`              | `json`                 |                                                    |
| Any other type          | `json`                 | Converted via Jackson; use `convertAndAddJsonVariable()` for POJOs |

For POJOs, prefer `convertAndAddJsonVariable()` over `variable()` — it serialises the object via Jackson before sending, ensuring it is stored as a JSON variable rather than relying on implicit conversion:

```java
return result.success()
        .variable("status", "COMPLETED")           // String
        .variable("count", 42)                     // Integer
        .variable("tags", List.of("a", "b"))       // auto-converted → json
        .convertAndAddJsonVariable("order", dto)   // POJO → json
        .build();
```

## `@ExternalWorker` Attributes

| Attribute      | Default          | Description                                                      |
|----------------|------------------|------------------------------------------------------------------|
| `topic`        | *(required)*     | The Flowable external worker topic name                          |
| `lockDuration` | global default   | ISO-8601 lock duration (e.g. `"PT10M"`)                         |
| `maxJobs`      | global default   | Max jobs to acquire per poll cycle                               |
| `concurrency`  | global default   | Max concurrent job executions for this topic                     |

Attribute values of `-1` (for numeric fields) and `""` (for `lockDuration`) mean "inherit from global properties."

## How It Works

1. On startup, `ExternalWorkerBeanPostProcessor` scans all Spring beans for `@ExternalWorker` methods, validates their signatures, and registers a `WorkerEndpoint` for each one.

2. `TaskBridgeLifecycle` (a `SmartLifecycle`, phase `Integer.MAX_VALUE - 100`) starts one `TopicSubscription` per topic and subscribes each to its Redis channel (`{prefix}{topic}`) via a shared `RedisMessageListenerContainer`.

3. Each `TopicSubscription` runs a poll loop on a virtual thread. On startup it immediately calls `acquireJobs` to pick up any backlog from before the worker started, then parks on a `Semaphore.tryAcquire(fallbackPollIntervalMs)`.

4. The workflow engine creates an external worker job and commits the transaction. `taskbridge-server`'s `ExternalJobCreatedListener` fires on `ENTITY_CREATED` (post-commit) and publishes `"work_available"` to the channel `{prefix}{topic}` via `StringRedisTemplate`.

5. `RedisWakeupSubscriber` receives the message and calls `TopicSubscription.wakeUp()`, which releases the semaphore. The poll loop unblocks and calls `acquireJobs` on Flowable's native REST API (`POST /external-job-api/acquire/jobs`).

6. Acquired jobs are dispatched to a `newVirtualThreadPerTaskExecutor`. A `Semaphore(concurrency)` limits how many jobs run in parallel per topic.

7. `WorkerMethodInvoker` calls the `@ExternalWorker` method, injecting the declared parameters. The return value (or exception) is translated to a `completeJob`, `failJob`, or `bpmnError` call against Flowable's native REST API.

**Batch re-poll:** if `acquireJobs` returns a full batch (`jobs.size() >= maxJobs`), the subscription signals itself immediately so the next poll fires without waiting — this drains a backlog as fast as Flowable serves it.

**Fallback timer:** if no Redis wakeup arrives within `fallback-poll-interval-ms`, the semaphore times out and a poll fires anyway. This is the safety net for any pub/sub message that is dropped — wakeup-via-Redis is best-effort, not durable.

**Error isolation:** if `completeJob`, `failJob`, or `bpmnError` itself fails (e.g. network error), TaskBridge logs the error and moves on. The job's lock will expire in Flowable and another worker can re-acquire it. Retrying the lifecycle call risks double-processing, which is worse than a single missed completion.

**Graceful shutdown:** `TaskBridgeLifecycle.stop()` calls `TopicSubscription.stop()` on every active subscription, which interrupts the poll thread and calls `ExecutorService.close()` — Java 21's combined `shutdown()` + `awaitTermination()` — so in-flight jobs are allowed to finish before the JVM exits.

## Configuration Reference

### Server (`taskbridge.server.*`)

| Property               | Default       | Description                                                  |
|------------------------|---------------|--------------------------------------------------------------|
| `enabled`              | `true`        | Set to `false` to disable the server module entirely         |
| `redis-channel-prefix` | `taskbridge:` | Prefix prepended to the topic name to form the channel name  |

### Client (`taskbridge.client.*`)

| Property                   | Default           | Description                                                             |
|----------------------------|-------------------|-------------------------------------------------------------------------|
| `flowable-base-url`        | *(required)*      | Base URL of the Flowable workflow service (e.g. `http://engine:8080`)   |
| `username`                 | `""`              | HTTP Basic auth username (omit for unauthenticated)                     |
| `password`                 | `""`              | HTTP Basic auth password                                                |
| `worker-id`                | `worker-<uuid>`   | Unique identifier for this worker instance                              |
| `redis-channel-prefix`     | `taskbridge:`     | Must match the server's `redis-channel-prefix`                          |
| `lock-duration`            | `PT5M`            | Default ISO-8601 lock duration (overridden per topic via annotation)    |
| `max-jobs`                 | `5`               | Default max jobs per acquire call                                       |
| `concurrency`              | `4`               | Default max concurrent job executions per topic                         |
| `fallback-poll-interval-ms` | `30000`          | How long to wait before polling even without a Redis wakeup             |
| `acquire-jitter-ms`        | `100`             | Random jitter added after a wakeup signal (spread out competing workers)|
| `retry-backoff-ms`         | `5000`            | Initial backoff after an acquire error                                  |
| `max-retry-backoff-ms`     | `30000`           | Maximum backoff cap                                                     |
| `enabled`                  | `true`            | Set to `false` to disable the client module entirely                    |

## Requirements

- Java 21
- Spring Boot 3.5+
- Flowable OSS 7.x (server module only)
- Redis (required by both modules)

## Contributing

Contributions are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) for how to build locally,
run the test suite, and submit a pull request.

## License

[Apache 2.0](LICENSE)
