# Exclusive-aware job acquire

`taskbridge-server` changes how Flowable picks external worker jobs to hand out, so that exclusive jobs can no longer
block an acquire. This page explains the problem, the fix and the alternatives that were tried.

## The problem

An external worker task can be **exclusive** (Flowable's default): only one exclusive job of a process instance may
run at a time. Flowable enforces this during the acquire. For every exclusive job in the batch it also locks the job's
process instance, all in one transaction.

If that instance lock fails, Flowable rolls back the **whole** batch, not just the one job. After its retries it
returns an empty list. That happens in two situations:

- **Two exclusive jobs of one process instance land in the same batch.** The first one locks the instance and the
  second one fails. Nothing changes between polls, so every later batch fails the same way and the jobs stay unlocked
  for good. Any worker asking for more than one job (`maxJobs > 1`) can hit this. A typical case is a non-interrupting
  event subprocess receiving two callbacks for the same case.
- **A job of an instance is already being worked on.** Its waiting sibling sits at the front of the queue, every batch
  that includes it fails, and jobs of other process instances on the same topic wait behind it.

Flowable's own acquire query has no condition on the process instance lock, and its public APIs have no way to add
one. Flowable's official worker client avoids the problem by asking for one job per poll by default.

## The fix

The query that picks candidate jobs now leaves out jobs Flowable would refuse:

1. **In SQL:** an exclusive job is skipped while its process instance holds a live lock (`LOCK_TIME_` not yet passed).
2. **In Java:** a batch keeps only the first exclusive job of each process instance.

Locking itself is untouched. Flowable still locks the job and the process instance, so "one exclusive job per process
instance at a time" holds exactly as before. The filter only stops Flowable from being offered jobs it would refuse.

### How it plugs in

| Piece | Role |
|---|---|
| `ExclusiveAwareExternalWorkerJob.xml` | Copy of Flowable's `selectExternalWorkerJobsToExecute` plus a `NOT EXISTS` on the locked process instance. The current time is passed from the engine clock, the same clock Flowable uses to write `LOCK_TIME_`. |
| `ExclusiveAwareExternalWorkerJobDataManager` | Extends Flowable's `MybatisExternalWorkerJobDataManager` and overrides only `findExternalJobsToExecute`, the method behind every acquire (REST `/acquire/jobs` and Java `acquireAndLock`). All other methods are inherited. |
| `ExclusiveAwareAcquire.install(...)` | Registers the mapper on the engine (`setCustomMybatisXMLMappers`) and the data manager on the job service (`addJobServiceConfigurator`). |
| `TaskBridgeServerAutoConfiguration` | Calls `install` by default. |

Workers and the REST API are unchanged.

### Configuration

| Property | Default | Meaning |
|---|---|---|
| `taskbridge.server.exclusive-acquire.enabled` | `true` | Turns the fix off when `false`. |
| `taskbridge.server.exclusive-acquire.lookahead` | `20` | Extra rows a batch reads so it still fills after dropping later exclusive jobs of the same instance. Jobs of locked instances are excluded in SQL and never use these rows. |

## Results

Measured on SQL Server 2022 with `READ_COMMITTED_SNAPSHOT ON`, running in Docker under emulation, so compare the
columns rather than the absolute times.

| Scenario | Stock Flowable | With the fix |
|---|---|---|
| 4 workers, 100 instances × 2 exclusive jobs, `maxJobs=5` | **0 of 200 done in 60 s**, 9.9k rolled-back transactions | **200 done in 0.6 s**, 50 acquire calls |
| Same, stock with `maxJobs=1` (Flowable's client default) | 1.0 s, 230 acquire calls | – |
| 100 instances × 2 exclusive jobs, nothing completed, 30 calls | 0 jobs, every call empty | 100 jobs, one per instance |
| Normal acquire, 3,000 jobs on other topics in the table | 10.4 ms, 31 statements | 9.8 ms, 31 statements |

To re-run it (needs Docker, takes a few minutes; it is skipped in a normal build):

```bash
mvn test -pl taskbridge-server -Dtest=SqlServerAcquireBenchmarkSpec -Dtaskbridge.benchmark=true
```

The report is printed at the end of the run. Re-run it after a Flowable upgrade.

## Alternatives considered

1. **Workers ask for one job at a time (`maxJobs = 1`).** No permanent stall, but more acquire calls, and jobs still
   wait behind a blocked one.
2. **Worker falls back to one-at-a-time after an empty batch.** Fixes the stall, but costs an extra REST call on every
   empty poll, and jobs still wait behind a blocked one.
3. **Filter in Java after reading a page of candidates.** Fixes the stall, but blocked jobs still take up the page.
   Once more than `lookahead + maxJobs` of them pile up, every batch is empty until running jobs finish.
4. **Filter in SQL (chosen).** Blocked jobs never take up the page, there are no extra calls, and there is no measurable
   cost.

## Known limits and maintenance

- **Copied query.** `ExclusiveAwareExternalWorkerJob.xml` copies Flowable's statement. On every Flowable upgrade,
  compare it with `selectExternalWorkerJobsToExecute` in `flowable-job-service`. The control test in
  `ExclusiveAwareAcquireEngineSpec` fails if the stock behaviour changes.
- **Flowable internals.** The data manager and the configuration hooks are internal Flowable classes and can change
  between releases.
- **Siblings still wait.** The second exclusive job of an instance runs only after the first finishes. That is the
  exclusivity rule. Workers are woken only when a job is created, so the sibling is picked up on the next poll.
- **Racing workers.** Two workers can pick different jobs of one instance at the same moment. The loser's lock fails
  and Flowable's acquire retry runs the query again, which then skips the job.
- **CMMN.** Case instance jobs are passed through unfiltered.
- **Databases.** Tested on H2 and SQL Server. The SQL is standard and paging uses Flowable's per-database
  placeholders, but other databases have not been run.
