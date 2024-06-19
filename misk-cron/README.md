## misk-cron
This module gives Misk services a way to run scheduled tasks using [cron](https://en.wikipedia.org/wiki/Cron) syntax. [Leases](https://github.com/cashapp/wisp/tree/main/wisp-lease) are used to ensure that only one instance of the service executes the schedule at a time.
Note that there are **not** strong guarantees on task execution; tasks can be delayed or missed entirely for many reasons, including if the instance currently holding the lease is degraded or if it dies completely while executing the task. This module is not a good choice for highly sensitive, business critical needs.

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
## Cron Task Distribution
Please keep in mind that the current behavior of the leases is that a single instance of the service runs all the ready cron tasks. 

If you wish to distribute the tasks across the fleet of pods, you would need to do the following:
- Update the `cronLeaseBehavior` within the `CronModule` to be equal to `ONE_LEASE_PER_CRON`

Note that there are **no** strong guarantees on lease distribution if you take this approach: **all leases for tasks could end up on a single pod**.