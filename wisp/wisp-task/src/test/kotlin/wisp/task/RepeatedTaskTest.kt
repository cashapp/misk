package wisp.task

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import wisp.task.exception.FailedTaskException
import kotlin.test.assertNotNull

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
        ) { _: String, _: TaskConfig ->
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
        ) { _: String, _: TaskConfig ->
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
        ) { _: String, _: TaskConfig ->
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

    data class MyTaskConfig(
        val foo: String,
        val allResults: MutableList<String> = mutableListOf()
    ) : TaskConfig()

    @Test
    fun `Using custom task config works`() {
        val myClassConfig = MyTaskConfig(foo = "foo")

        val repeatedTask = RepeatedTask(
            name = "taskName",
            repeatedTaskConfig = repeatedTaskConfig,
            taskConfig = myClassConfig
        ) { _: String, taskConfig: TaskConfig ->
            val config = taskConfig as MyTaskConfig
            assertNotNull(config)
            Status.NO_RESCHEDULE
        }
        repeatedTask.startUp()
        assertTrue(repeatedTask.isRunning())
        repeatedTask.shutDown()
    }

}
