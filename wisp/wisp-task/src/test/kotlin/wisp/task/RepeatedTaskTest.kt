package wisp.task

import io.micrometer.core.instrument.Clock
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import wisp.task.exception.FailedTaskException
import kotlin.test.assertNotNull

internal class RepeatedTaskTest {
    private val repeatedTaskConfig = RepeatedTaskConfig(
        timeBetweenRunsMs = 100L
    )

    private val collectorRegistry = CollectorRegistry()
    private val prometheusRegistry = PrometheusMeterRegistry(
      PrometheusConfig.DEFAULT, collectorRegistry, Clock.SYSTEM
    )

    private fun durationTest(status: Status, result: String) {
        val repeatedTask = RepeatedTask(
          name = "taskName",
          meterRegistry = prometheusRegistry,
          repeatedTaskConfig = repeatedTaskConfig
        ) { _: String, _: TaskConfig ->
            status
        }
        assertFalse(repeatedTask.isRunning())
        repeatedTask.startUp()
        Thread.sleep(repeatedTaskConfig.timeBetweenRunsMs * 2L)
        assertTrue(repeatedTask.isRunning())
        val summary = prometheusRegistry
          .get(RepeatedTaskMetrics.DURATION_SUMMARY_NAME)
          .tag("name", "taskName")
          .tag("result", result)
          .summary()
        assert(summary.count() > 1)
    }

    @Test
    fun successfulTaskRecordsDuration() {
        durationTest(Status.OK, "success")
    }

    @Test
    fun failedTaskRecordsDuration() {
        durationTest(Status.FAILED, "failed")
    }

    @Test
    fun noWorkTaskRecordsDuration() {
        durationTest(Status.NO_WORK, "no_work")
    }

    private fun resultMetricTest(status: Status, counterName: String) {
        val repeatedTask = RepeatedTask(
          name = "taskName",
          meterRegistry = prometheusRegistry,
          repeatedTaskConfig = repeatedTaskConfig
        ) { _: String, _: TaskConfig ->
            status
        }
        repeatedTask.startUp()
        Thread.sleep(repeatedTaskConfig.timeBetweenRunsMs * 2L)
        repeatedTask.shutDown()
        val counter = prometheusRegistry
          .get(counterName)
          .counter()
        assert(counter.count() > 1.0)
    }

    @Test
    fun successfulTaskReportsSuccessMetric() {
        resultMetricTest(Status.OK, RepeatedTaskMetrics.SUCCESS_COUNTER_NAME)
    }

    @Test
    fun failedTaskReportsFailedMetric() {
        resultMetricTest(Status.FAILED, RepeatedTaskMetrics.FAILED_COUNTER_NAME)
    }

    @Test
    fun noWorkTaskReportsNoWorkMetric() {
        resultMetricTest(Status.NO_WORK, RepeatedTaskMetrics.NO_WORK_COUNTER_NAME)
    }

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
