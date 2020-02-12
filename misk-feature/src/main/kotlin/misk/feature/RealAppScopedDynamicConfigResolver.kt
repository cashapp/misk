package misk.feature

import misk.logging.getLogger

internal class RealAppScopedDynamicConfigResolver<T : ValidatableConfig<T>> constructor(
  private val appName: String,
  private val configName: String,
  private val dynamicConfig: DynamicConfig,
  private val failureModeDefault: T
) : AppScopedDynamicConfigResolver<T> {
  private val logger = getLogger<AppScopedDynamicConfigResolver<T>>()

  override fun resolveConfig(): T {
    require(!configName.startsWith(appName)) {
      "Redundant '$appName' prefix for ConsumerConfigResolver managed app config '$configName'"
    }

    val featureName = listOf(appName, configName).joinToString("-")
    return try {
      val feature = Feature(featureName)
      val config = dynamicConfig.getJson(feature, failureModeDefault::class.java)
      config.validate()
      config
    } catch (e: Exception) {
      logger.warn(e) {
        "Failed to retrieve or parse JSON for config override '$featureName', using $failureModeDefault"
      }
      failureModeDefault
    }
  }
}
