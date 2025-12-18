package wisp.ratelimiting

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry

class RateLimitPrunerMetrics(meterRegistry: MeterRegistry) {
  val bucketsPruned: Counter =
    Counter.builder(PRUNED_BUCKETS_COUNTER_NAME)
      .description("count of rate limit buckets pruned")
      .register(meterRegistry)

  val pruningDuration: DistributionSummary =
    DistributionSummary.builder(PRUNING_DURATION)
      .description("duration in ms of rate limit bucket pruning")
      .percentilePrecision(4)
      .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
      .register(meterRegistry)

  companion object {
    const val PRUNING_DURATION = "rate_limit_pruner_pruning_duration"
    const val PRUNED_BUCKETS_COUNTER_NAME = "rate_limit_pruner_pruned_buckets"
  }
}
