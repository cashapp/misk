package misk.feature.launchdarkly

import misk.feature.Attributes
import misk.feature.DynamicConfig
import misk.feature.Feature
import misk.feature.FeatureFlags
import javax.inject.Singleton

@Singleton
class LaunchDarklyDynamicConfig(private val featureFlags: FeatureFlags) : DynamicConfig {
  companion object {
    const val KEY = "dynamic_flag"
    val ATTRIBUTES = Attributes(anonymous = true)
  }

  override fun getBoolean(feature: Feature): Boolean {
    return featureFlags.getBoolean(feature, KEY, ATTRIBUTES)
  }

  override fun getInt(feature: Feature): Int {
    return featureFlags.getInt(feature, KEY, ATTRIBUTES)
  }

  override fun getString(feature: Feature): String {
    return featureFlags.getString(feature, KEY, ATTRIBUTES)
  }

  override fun <T : Enum<T>> getEnum(feature: Feature, clazz: Class<T>): T {
    return featureFlags.getEnum(feature, KEY, clazz, ATTRIBUTES)
  }

  override fun <T> getJson(feature: Feature, clazz: Class<T>): T {
    return featureFlags.getJson(feature, KEY, clazz, ATTRIBUTES)
  }
}
