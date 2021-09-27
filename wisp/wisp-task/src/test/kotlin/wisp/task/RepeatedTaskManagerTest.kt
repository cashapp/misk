package wisp.task

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wisp.task.exception.NoTaskFoundException
import kotlin.test.assertSame

internal class RepeatedTaskManagerTest {
  private val repeatedTaskConfig = RepeatedTaskConfig(
    timeBetweenRunsMs = 100L
  )

  @Test
  fun createTask() {
    val manager = RepeatedTaskManager()
    val newTask = manager.createTask(
      name = "taskName",
      repeatedTaskConfig = repeatedTaskConfig
    ) { name: String, taskConfig: TaskConfig ->
      Status.NO_RESCHEDULE
    }

    assertFalse(newTask.isRunning())
  }

  @Test
  fun getTask() {
    val taskName = "taskName"
    val manager = RepeatedTaskManager()
    val newTask = manager.createTask(
      name = taskName,
      repeatedTaskConfig = repeatedTaskConfig
    ) { name: String, taskConfig: TaskConfig ->
      Status.NO_RESCHEDULE
    }

    val repeatedTask = manager.getTask(taskName)
    assertSame(newTask, repeatedTask)
  }

  @Test
  fun taskExists() {
    val manager = RepeatedTaskManager()
    val repeatedTask1 = manager.createTask(
      name = "taskName",
      repeatedTaskConfig = repeatedTaskConfig
    ) { name: String, taskConfig: TaskConfig ->
      Status.NO_RESCHEDULE
    }
    assertTrue(manager.taskExists(repeatedTask1.name))
    assertFalse(manager.taskExists("unknownTask"))
  }

  @Test
  fun `Getting a Task that does not exists throws a NoTaskFoundException`() {
    val manager = RepeatedTaskManager()
    assertThrows<NoTaskFoundException> {
      manager.getTask("unknownTask")
    }
  }

  @Test
  fun shutDown() {
    val manager = RepeatedTaskManager()
    val repeatedTask1 = manager.createTask(
      name = "taskName",
      repeatedTaskConfig = repeatedTaskConfig
    ) { name: String, taskConfig: TaskConfig ->
      Thread.sleep(100L)
      Status.OK
    }
    val repeatedTask2 = manager.createTask(
      name = "differentTask",
      repeatedTaskConfig = repeatedTaskConfig
    ) { name: String, taskConfig: TaskConfig ->
      Thread.sleep(100L)
      Status.OK
    }

    repeatedTask1.startUp()
    repeatedTask2.startUp()
    assertTrue(repeatedTask1.isRunning())
    assertTrue(repeatedTask2.isRunning())

    Thread.sleep(200L)

    manager.shutDown()
    assertFalse(repeatedTask1.isRunning())
    assertFalse(repeatedTask2.isRunning())
  }
}
