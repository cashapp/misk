package misk.perf

/**
 * Configuration for the [PauseDetector]
 */
data class PauseDetectorConfig(
  /** The delay between detector runs. If 0, the detector runs in a spin loop. */
  val resolutionMillis: Long = 1,

  /** The maximum number of invocations to [Thread.sleep] to await the configured resolution */
  val maxSleepIterations: Int = 5,

  /** The minimum number of millis paused before logging at info. -1 for never. */
  val logInfoMillis: Long = 1000,

  /** The minimum number of millis paused before logging at warn. -1 for never. */
  val logWarnMillis: Long = -1,

  /** The minimum number of millis paused before logging at error. -1 for never. */
  val logErrorMillis: Long = -1,

  /** The minimum number of millis required to trigger an update of the histogram */
  val metricsUpdateFloor: Long = 0,
)
