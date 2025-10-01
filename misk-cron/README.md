## misk-cron
This module gives Misk services a way to run scheduled tasks using [cron](https://en.wikipedia.org/wiki/Cron) syntax. [Leases](https://github.com/cashapp/wisp/tree/main/wisp-lease) are used to ensure that only one instance of the service executes the schedule at a time.

Note that there are **not** strong guarantees on task execution; tasks can be delayed or missed entirely for many reasons, including if the instance currently holding the lease is degraded or if it dies completely while executing the task. This module is not a good choice for highly sensitive, business critical needs.

**For business-critical tasks consider migrating to [Temporal](https://temporal.io/) or other solutions providing at-least-once execution guarantees.**

## Usage
1. Create a `Runnable`
    ```kotlin
    @CronPattern("* * * * *") // runs every 1 minute
    class MyCronTask : Runnable {
      override fun run() {
        println("Doing some work here")
      }
    }
    ```
2. Install the `CronModule` and a `CronEntryModule` for your task.
    ```kotlin
    class MyAppModule : KAbstractModule {
      override fun configure() {
        install(CronModule(ZoneId.of("America/Los_Angeles")))
        install(CronEntryModule.create<MyCronTask>())
      }
    }
    ```

## Execution Models

### Single-lease mode (default)
- One cluster-wide lease controls all cron tasks
- Only one pod in the cluster executes cron tasks at a time
- **Best when:**
   - Tasks idempotency is uncertain or unverified
   - You have only a small number of tasks with non-overlapping schedules 
   - You want a slightly simpler operational model (all tasks run on a single pod)

### Distributed mode (multiple lease execution)
- Each cron task has its own lease
- Multiple pods can run tasks concurrently, increasing resource utilization and fault tolerance
- **Best when:**
   - You have multiple independent tasks with overlapping schedules
   - Tasks are idempotent
   - You want to avoid a single point of failure

## Multiple-lease Execution

To enable multiple-lease mode of execution, set `useMultipleLeases = true`. In this mode, leases are granted per task, allowing tasks to run in parallel across the cluster:

```kotlin
class MyAppModule : KAbstractModule {
  override fun configure() {
    install(CronModule(
      zoneId = ZoneId.of("America/Los_Angeles"),
      useMultipleLeases = true
    ))
    install(CronEntryModule.create<MyCronTask>())
  }
}
```

## Migration to Distributed (Multiple-lease) Mode

### Callout
During rolling deployments, cron tasks may briefly run twice (once per old pod and once per new pod).
**To mitigate:**
- Ensure tasks are idempotent, or
- Deploy between scheduled runs if tasks run at low to moderate frequency (e.g., once a day or every few hours)
- Deploy during a cron downtime window if tasks run at very high frequency (e.g., every minute).

### Pre-migration Assessment
**Task frequency:**
- Low-frequency tasks (hourly, daily): safe for migration.
- High-frequency tasks (every minute): ensure idempotency or accept possible overlaps.

**Task idempotency:**
- Tasks should not produce incorrect results or side effects when re-run.

### Migration Steps
1. Deploy with `useMultipleLeases = false` (no behavior change).
2. Verify deployment stability across all environments.
3. **Redeploy** with `useMultipleLeases = true` (requires full redeploy of all pods, not a runtime config toggle).
4. Monitor logs/metrics to validate task distribution and performance.
5. If issues arise, redeploy with `useMultipleLeases = false`.