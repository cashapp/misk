package wisp.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry

/**
 * Metrics for repeated tasks:
 *
 *   Duration
 *   Success Count
 *   Failures Count
 *   Count of No Work Required
 */
class RepeatedTaskMetrics(meterRegistry: MeterRegistry) {

    internal fun taskDuration(
        meterRegistry: MeterRegistry,
        name: String,
        result: String,
    ): DistributionSummary =
        DistributionSummary.builder(DURATION_SUMMARY_NAME)
            .description("count and duration in ms of periodic tasks")
            .tag("name", name)
            .tag("result", result)
            .percentilePrecision(4)
            .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
            .publishPercentileHistogram()
            .register(meterRegistry)

    internal val successCount: Counter = Counter.builder(SUCCESS_COUNTER_NAME)
        .description("count of successful repeated tasks")
        .register(meterRegistry)

    internal val failedCount: Counter = Counter.builder(FAILED_COUNTER_NAME)
        .description("count of repeated tasks failures")
        .register(meterRegistry)

    internal val noWorkCount: Counter = Counter.builder(NO_WORK_COUNTER_NAME)
        .description("count of repeated tasks invocations with no work to do")
        .register(meterRegistry)

    companion object {
        const val DURATION_SUMMARY_NAME = "repeated.task.duration"
        const val SUCCESS_COUNTER_NAME = "repeated.task.success"
        const val FAILED_COUNTER_NAME = "repeated.task.failed"
        const val NO_WORK_COUNTER_NAME = "repeated.task.no.work"
    }
}
