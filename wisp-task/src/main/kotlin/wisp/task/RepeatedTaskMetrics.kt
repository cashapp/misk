package wisp.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry

class RepeatedTaskMetrics(meterRegistry: MeterRegistry) {

  internal var taskDuration: DistributionSummary =
    DistributionSummary.builder("repeated_task_duration")
      .description("count and duration in ms of periodic tasks")
      .tag("name", "result")
      .percentilePrecision(4)
      .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
      .publishPercentileHistogram()
      .register(meterRegistry)

  internal var successCount: Counter = Counter.builder("repeated_task_success_count")
    .description("count of successful repeated tasks")
    .register(meterRegistry)

  internal var retryCount: Counter = Counter.builder("repeated_task_retry_count")
    .description("count of repeated tasks retries")
    .register(meterRegistry)

  internal var noWorkCount: Counter = Counter.builder("repeated_task_no_work_count")
    .description("count of repeated tasks invocations with no work to do")
    .register(meterRegistry)
}
