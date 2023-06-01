package wisp.launchdarkly

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry

class LaunchDarklyClientMetrics(meterRegistry: MeterRegistry) {

  internal fun initializationDuration(
    meterRegistry: MeterRegistry
  ): DistributionSummary =
    DistributionSummary.builder(HISTO_DURATION_NAME)
      .description("count and duration in ms of ld client initialization")
      .percentilePrecision(4)
      .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
      .publishPercentileHistogram()
      .register(meterRegistry)

  internal val successCount: Counter = Counter.builder(SUCCESS_COUNTER_NAME)
    .description("count of successful ld client initializations")
    .register(meterRegistry)

  internal val failedCount: Counter = Counter.builder(FAILED_COUNTER_NAME)
    .description("count of ld client initialization failures")
    .register(meterRegistry)

  companion object {
    const val HISTO_DURATION_NAME = "histo_ld_initialization_duration_ms"
    const val SUCCESS_COUNTER_NAME = "ld_initialization_success_total"
    const val FAILED_COUNTER_NAME = "ld_initialization_failed_total"
  }
}