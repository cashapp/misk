package wisp.task

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wisp.task.exception.FailedTaskException

internal class RepeatedTaskTest {
  private val repeatedTaskConfig = RepeatedTaskConfig(
    timeBetweenRunsMs = 100L
  )

  @Test
  fun noRescheduleStopsRepeatedTask() {
    var counter = 0
    val repeatedTask = RepeatedTask(
      name = "taskName",
      repeatedTaskConfig = repeatedTaskConfig
    ) { name: String, taskConfig: TaskConfig ->
      counter++
      Status.NO_RESCHEDULE
    }
    assertFalse(repeatedTask.isRunning())
    repeatedTask.startUp()
    Thread.sleep(repeatedTaskConfig.timeBetweenRunsMs * 2L)
    assertFalse(repeatedTask.isRunning())
    assertEquals(1, counter)
  }

  @Test
  fun repeatedTaskRepeatsIfResultStatusOk() {
    var counter = 0
    val repeatedTask = RepeatedTask(
      name = "taskName",
      repeatedTaskConfig = repeatedTaskConfig
    ) { name: String, taskConfig: TaskConfig ->
      counter++
      Status.OK
    }
    assertFalse(repeatedTask.isRunning())
    repeatedTask.startUp()
    Thread.sleep(repeatedTaskConfig.timeBetweenRunsMs * 2L)
    assertTrue(repeatedTask.isRunning())
    repeatedTask.shutDown()
    assertTrue(counter > 1)
  }

  @Test
  fun failedTaskRetries() {
    var counter = 0
    var retryCounter = 0
    val repeatedTask = RepeatedTask(
      name = "taskName",
      repeatedTaskConfig = repeatedTaskConfig
    ) { name: String, taskConfig: TaskConfig ->
      retryCounter++
      if (retryCounter < 3) {
        throw FailedTaskException()
      }
      counter++
      Status.NO_RESCHEDULE
    }
    assertFalse(repeatedTask.isRunning())
    repeatedTask.startUp()
    assertTrue(repeatedTask.isRunning())

    // need to wait some time for the tasks to repeat and finish
    Thread.sleep(repeatedTaskConfig.timeBetweenRunsMs * 5L)

    repeatedTask.shutDown()
    assertEquals(1, counter)
    assertEquals(3, retryCounter)

  }
  class MyTaskConfig(
    val foo: String,
    val allResults: MutableList<String> = mutableListOf()
  ): TaskConfig()

  @Test
  fun `Using custom task config works`() {
    val myClassConfig = MyTaskConfig("foo")

    val repeatedTask = RepeatedTask(
      name = "taskName",
      repeatedTaskConfig = repeatedTaskConfig,
      taskConfig = myClassConfig
    ) { name: String, taskConfig: TaskConfig ->
      val config = taskConfig as MyTaskConfig

      Status.NO_RESCHEDULE
    }

    }

}
