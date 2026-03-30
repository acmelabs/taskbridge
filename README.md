# TaskBridge

Long-poll external worker library for [Flowable](https://flowable.org).

[![Maven Central](https://img.shields.io/maven-central/v/app.acmelabs.taskbridge/taskbridge.svg)](https://search.maven.org/search?q=g:app.acmelabs.taskbridge)
[![Build Status](https://img.shields.io/github/actions/workflow/status/acmelabs/taskbridge/build.yml)](https://github.com/acmelabs/taskbridge/actions)

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

## Worker Method Signatures

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
2. `taskbridge-server` listener fires after transaction commit (`ENTITY_CREATED` on `ExternalWorkerJobEntity`)
3. Listener wakes a parked long-poll request for that topic
4. Worker receives the wake-up signal, fetches and locks the job
5. Worker executes the `@ExternalWorker` method
6. Worker completes or fails the job via REST

If no notification is received (network blip, restart), the long-poll times out
and the worker immediately re-polls — acting as a built-in fallback.

## Configuration Reference

### Server (`taskbridge.server.*`)

| Property | Default | Description |
|----------|---------|-------------|
| `enabled` | `true` | Enable/disable the server module |
| `default-timeout-ms` | `30000` | How long to park a request before returning empty |
| `default-lock-duration` | `PT5M` | ISO-8601 lock duration for acquired jobs |
| `max-jobs-per-poll` | `5` | Maximum jobs returned per poll |

### Client (`taskbridge.client.*`)

| Property | Default | Description |
|----------|---------|-------------|
| `base-url` | *(required)* | Base URL of the workflow service |
| `worker-id` | `worker-<uuid>` | Unique identifier for this worker instance |
| `poll-timeout-ms` | `30000` | Long-poll timeout in milliseconds |
| `lock-duration` | `PT5M` | ISO-8601 default lock duration |
| `max-jobs` | `5` | Default jobs per poll |
| `concurrency` | `4` | Default concurrent job processing per topic |
| `max-retry-attempts` | `3` | Connection error retries before backing off |
| `retry-backoff-ms` | `5000` | Initial backoff after connection error |
| `max-retry-backoff-ms` | `30000` | Maximum backoff cap |

## Requirements

- Java 17+
- Spring Boot 3.2+
- Flowable OSS 7.x (server module only)

## License

[MIT](LICENSE)
