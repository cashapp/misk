package misk.logging

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.feature.Attributes
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.tasks.RepeatedTaskQueue
import misk.tasks.Status
import java.time.Duration

@Singleton
class DynamicLogLevelService
@Inject
constructor(
  private val featureFlags: FeatureFlags,
  private val config: DynamicLoggingConfig,
  @DynamicLogLevel private val repeatedTaskQueue: RepeatedTaskQueue,

) : AbstractIdleService() {

  private val podName = System.getenv("MY_POD_NAME")
  private val controller: DynamicLogLevelController = DynamicLogLevelController()
  private val feature = Feature(config.feature_flag_name)

  override fun startUp() {
    if (!config.enabled) {
      logger.info { "DynamicLogLevelService is disabled" }
      return
    }
    logger.info { "Starting DynamicLogLevelService" }
    repeatedTaskQueue.scheduleWithBackoff(Duration.ofSeconds(20), Duration.ZERO) {
      updateLoggingLevels()
      Status.OK
    }
  }

  override fun shutDown() {
    logger.info { "Shutting down DynamicLogLevelService" }
    // No cleanup needed - the RepeatedTaskQueue will be stopped by the ServiceManager
  }

  fun updateLoggingLevels() {
    val commaDelimitedPairs = featureFlags.getString(feature, "test",
      Attributes(mapOf("pod_name" to podName))
    )
    controller.refresh(commaDelimitedPairs)
  }

  companion object {
    private val logger = getLogger<DynamicLogLevelService>()
    const val UNSET = "UNSET"
    const val POD_NAME = "MY_POD_NAME"
  }
}