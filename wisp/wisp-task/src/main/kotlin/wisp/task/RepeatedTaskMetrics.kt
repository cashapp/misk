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

    internal var taskDuration: DistributionSummary =
        DistributionSummary.builder("repeated.task")
            .description("count and duration in ms of periodic tasks")
            .tag("name", "result")
            .percentilePrecision(4)
            .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
            .publishPercentileHistogram()
            .register(meterRegistry)

    internal var successCount: Counter = Counter.builder("repeated.task.success")
        .description("count of successful repeated tasks")
        .register(meterRegistry)

    internal var failedCount: Counter = Counter.builder("repeated.task.failed")
        .description("count of repeated tasks failures")
        .register(meterRegistry)

    internal var noWorkCount: Counter = Counter.builder("repeated.task.no.work")
        .description("count of repeated tasks invocations with no work to do")
        .register(meterRegistry)
}
