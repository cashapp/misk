package wisp.task

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

}
