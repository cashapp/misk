# wisp-task

This module contains code to assist with running tasks.

[RepeatedTask](https://github.com/cashapp/misk/blob/master/wisp-task/src/main/kotlin/wisp/task/RepeatedTask.kt)
runs a repeated task at the user controlled rate based on the
[kotlin-retry library](https://github.com/michaelbull/kotlin-retry).  For convenience a
[RepeatedTaskManager](https://github.com/cashapp/misk/blob/master/wisp-task/src/main/kotlin/wisp/task/RepeatedTaskManager.kt)
is available to help manage the
[RepeatedTasks](https://github.com/cashapp/misk/blob/master/wisp-task/src/main/kotlin/wisp/task/RepeatedTask.kt).

It's possible to specify your own retry policy for the task.  See
[kotlin-retry library](https://github.com/michaelbull/kotlin-retry) for more details and examples.
The default retry policy instructions are to retry on all exceptions with a Binary Exponential
backoff delay - which itself is configured from the supplied
[RepeatedTaskConfig](https://github.com/cashapp/misk/blob/master/wisp-task/src/main/kotlin/wisp/task/RepeatedTaskConfig.kt).

## Usage

Create a repeated task and start it running.

```kotlin
val taskConfig = TaskConfig("taskName")
val repeatedTaskConfig = RepeatedTaskConfig(
  timeBetweenRunsMs = 10000L  // 10 sec delay between task runs or retries
)

val manager = RepeatedTaskManager()

val newTask = manager.createTask(
  name = taskConfig.name,
  repeatedTaskConfig = repeatedTaskConfig,
  taskConfig = taskConfig
) {
  // do task stuff
  // ...
  
  // if the task completes correctly, return OK.
  Status.OK
}

newTask.startUp()
```

You can shutdown a task or the manager which will shutdown all tasks. Using the above code:

```kotlin
// shut down the repeated task.
newTask.shutDown()

// shut down all repeated tasks known by the manager.
manager.shutDown()
```

The task to be run takes a TaskConfig (which could be loaded using
[wisp-config](https://github.com/cashapp/misk/tree/master/wisp-config)).

```kotlin
class MyTaskConfig(
  name: String, 
  val foo: String,
  val allResults: MutableList<String> = mutableListOf()
): TaskConfig(name)

val taskName = "myTask"
val anotherTask = manager.createTask(
  name = taskNamee,
  repeatedTaskConfig = repeatedTaskConfig,
  taskConfig = MyTaskConfig(taskName, "fooString")
) {
  
  // access the taskConfig
  println("Foo is ${it.foo}")
  
  // do task stuff
  // ...
  it.allResults.add("another result")

  // if the task completes correctly, return OK.
  Status.OK
}
```