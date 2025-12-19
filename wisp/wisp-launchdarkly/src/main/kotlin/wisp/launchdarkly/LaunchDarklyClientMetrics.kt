package wisp.launchdarkly

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

class LaunchDarklyClientMetrics(private val meterRegistry: MeterRegistry) {

  private var initSuccess: Counter = meterRegistry.counter(SUCCESS_COUNTER_NAME)

  private var initFailure: Counter = meterRegistry.counter(FAILED_COUNTER_NAME)

  fun onInitSuccess(duration: Long) {
    initSuccess.increment()
    meterRegistry.gauge(INITIALIZATION_DURATION_NAME, duration)
  }

  fun onInitFailure() {
    initFailure.increment()
  }

  companion object {
    const val INITIALIZATION_DURATION_NAME = "ld_initialization_duration_ms"
    const val SUCCESS_COUNTER_NAME = "ld_initialization_success_total"
    const val FAILED_COUNTER_NAME = "ld_initialization_failed_total"
  }
}
