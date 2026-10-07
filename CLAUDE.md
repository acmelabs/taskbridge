# TaskBridge

Redis-signalled external worker library for Flowable — Java 21, Spring Boot 3.5.

## Project structure

```
taskbridge/                        ← parent POM (app.acmelabs.taskbridge:taskbridge:1.0.0-SNAPSHOT)
├── taskbridge-server/             ← Flowable-side: publishes Redis signals on job creation
└── taskbridge-client/             ← Worker-side: subscribes to signals, acquires and dispatches jobs
```

### taskbridge-server

Drop into any app that runs Flowable. Auto-configuration registers a `FlowableEventListener` that fires after transaction commit on `ENTITY_CREATED` for `ExternalWorkerJobEntity`, publishing `"work_available"` to `taskbridge:{topic}` via `StringRedisTemplate`.

Key files:
- `ExternalJobCreatedListener` — the listener; `isFailOnException=false`, fires on `COMMITTED`
- `TaskBridgeServerAutoConfiguration` — wires the listener into `SpringProcessEngineConfiguration`
- `TaskBridgeServerProperties` — `taskbridge.server.enabled` (default `true`), `taskbridge.server.redis-channel-prefix` (default `"taskbridge:"`)

### taskbridge-client

Drop into any app that has `@ExternalWorker`-annotated beans. Auto-configuration discovers them, creates a `TopicSubscription` per topic, and wires each to the Redis wakeup channel.

Key files:
- `@ExternalWorker` — method annotation; `topic` required, `lockDuration`/`maxJobs`/`concurrency` optional (fall back to global properties)
- `ExternalWorkerBeanPostProcessor` — scans all beans, validates and registers `WorkerEndpoint` instances
- `WorkerMethodInvoker` — resolves parameters (`AcquiredJob`, `ExternalWorkerResultBuilder`, `Map`) and handles return types (`void`, `ExternalWorkerResult`, `Map`)
- `TopicSubscription` — one virtual thread per topic; `Semaphore`-based wakeup coalescing; `newVirtualThreadPerTaskExecutor` for job dispatch
- `RedisWakeupSubscriber` — `MessageListener` that routes channel messages to `TopicSubscription.wakeUp()`
- `TaskBridgeLifecycle` — `SmartLifecycle` (phase `MAX_VALUE-100`); starts/stops all subscriptions
- `TaskBridgeClientAutoConfiguration` — creates all beans; `@ConditionalOnProperty(taskbridge.client.enabled, default true)`
- `TaskBridgeClientProperties` — `taskbridge.client.*` prefix; key fields: `flowable-base-url`, `worker-id`, `username`, `password`, `redis-channel-prefix`, `lock-duration`, `max-jobs`, `concurrency`, `fallback-poll-interval-ms`

## Build and test

```bash
mvn test                        # all modules
mvn test -pl taskbridge-server  # server only
mvn test -pl taskbridge-client  # client only
```

## Technology decisions

**Virtual threads**: Each `TopicSubscription` runs its poll loop on a virtual thread (`Thread.ofVirtual()`). Job dispatch uses `Executors.newVirtualThreadPerTaskExecutor()`. Never use `synchronized` — use `Semaphore`, `ReentrantLock`, or `volatile` instead to avoid pinning carrier threads.

**Wakeup coalescing**: Redis signals are absorbed by a `Semaphore(0)`. `tryAcquire(fallbackPollIntervalMs)` serves as both the wakeup listener and the fallback timer. When a full batch is returned (`jobs.size() >= maxJobs`), the subscription releases the semaphore immediately to trigger a follow-up acquire without waiting.

**Flowable variable mapping**: The REST client maps 11 types: `string`, `short`, `integer`, `long`, `double`, `boolean`, `date`, `instant`, `localDate`, `localDateTime`, `json`. Unknown types fall back to `json` via `objectMapper.valueToTree()`.

**BPMN vs CMMN detection**: Acquire response fields differ. If `processInstanceId` is present → BPMN (`executionId`→`subScopeId`, `processDefinitionId`→`scopeDefinitionId`). Otherwise CMMN with direct `scopeId`/`scopeType`/`subScopeId`/`scopeDefinitionId`.

**Error handling**: `processJob` separates invoke errors from REST reporting errors — each in its own try-catch. A failed `completeJob`/`failJob` call is logged and dropped (lock expires naturally; Flowable re-queues). Double-processing risk from retrying is worse than single skip.

## Test framework

Spock 2.3 (Groovy 4.0), compiled by gmavenplus-plugin 3.0.2. All test classes are `*Spec`. Java helper fixtures (e.g. `TestWorkerBeans.java`) live in `src/test/java`.

**Async test pattern**: Never use `1 * mock.method()` in `then:` for calls that happen on background virtual threads — the call may have already fired before `then:` registers the interaction, yielding spurious "0 invocations". Instead capture results via `LinkedBlockingQueue` / `AtomicReference` in `given:`, block in `when:` (`queue.poll(2, SECONDS)`), and assert captured values in `then:`.

**`ArgumentCaptor` over closure matchers**: Groovy closures coerced as Mockito `ArgumentMatcher` don't work. Use `ArgumentCaptor.forClass(...)` with `captor.capture()` then assert `captor.value`.
