package wisp.launchdarkly

import io.micrometer.core.instrument.MeterRegistry

class LaunchDarklyClientMetrics(
  private val meterRegistry: MeterRegistry) {
  fun onInitSuccess(duration: Long) {
    // Counter metric do not work during the ld client startup
    meterRegistry.gauge(
      INITIALIZATION_SUCCESS_NAME,
      1.0
    )
    meterRegistry.gauge(
      INITIALIZATION_DURATION_NAME,
      duration,
    )
  }

  fun onInitFailure() {
    meterRegistry.gauge(
      INITIALIZATION_FAILED_NAME,
      1.0
    )
  }

  companion object {
    const val INITIALIZATION_DURATION_NAME = "ld_initialization_duration_ms"
    const val INITIALIZATION_SUCCESS_NAME = "ld_initialization_success"
    const val INITIALIZATION_FAILED_NAME = "ld_initialization_failed"
  }
}
