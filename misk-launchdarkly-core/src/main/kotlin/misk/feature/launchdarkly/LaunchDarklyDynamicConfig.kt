package misk.feature.launchdarkly

import misk.feature.Attributes
import misk.feature.DynamicConfig
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.feature.TrackerReference
import javax.inject.Singleton

@Singleton
class LaunchDarklyDynamicConfig(private val featureFlags: FeatureFlags) : DynamicConfig {
  companion object {
    const val KEY = "dynamic_flag"
    val ATTRIBUTES = Attributes(anonymous = true)
  }

  override fun getBoolean(feature: Feature) =
    featureFlags.getBoolean(feature, KEY, ATTRIBUTES)

  override fun getInt(feature: Feature) =
    featureFlags.getInt(feature, KEY, ATTRIBUTES)

  override fun getString(feature: Feature) =
    featureFlags.getString(feature, KEY, ATTRIBUTES)

  override fun <T : Enum<T>> getEnum(feature: Feature, clazz: Class<T>) =
    featureFlags.getEnum(feature, KEY, clazz, ATTRIBUTES)

  override fun <T> getJson(feature: Feature, clazz: Class<T>) =
    featureFlags.getJson(feature, KEY, clazz, ATTRIBUTES)

  override fun trackBoolean(feature: Feature, tracker: (Boolean) -> Unit) =
    featureFlags.trackBoolean(feature, KEY, ATTRIBUTES, tracker)

  override fun trackInt(feature: Feature, tracker: (Int) -> Unit) =
    featureFlags.trackInt(feature, KEY, ATTRIBUTES, tracker)

  override fun trackString(feature: Feature, tracker: (String) -> Unit) =
    featureFlags.trackString(feature, KEY, ATTRIBUTES, tracker)

  override fun <T : Enum<T>> trackEnum(
    feature: Feature,
    clazz: Class<T>,
    tracker: (T) -> Unit
  ) = featureFlags.trackEnum(feature, KEY, clazz, ATTRIBUTES, tracker)

  override fun <T> trackJson(
    feature: Feature,
    clazz: Class<T>,
    tracker: (T) -> Unit
  ) = featureFlags.trackJson(feature, KEY, clazz, ATTRIBUTES, tracker)
}
