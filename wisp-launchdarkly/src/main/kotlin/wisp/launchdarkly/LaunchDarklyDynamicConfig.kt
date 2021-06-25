package wisp.launchdarkly

import wisp.feature.Attributes
import wisp.feature.DynamicConfig
import wisp.feature.Feature
import wisp.feature.FeatureFlags
import java.util.concurrent.Executor

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

  override fun trackBoolean(feature: Feature, executor: Executor, tracker: (Boolean) -> Unit) =
    featureFlags.trackBoolean(feature, KEY, ATTRIBUTES, executor, tracker)

  override fun trackInt(feature: Feature, executor: Executor, tracker: (Int) -> Unit) =
    featureFlags.trackInt(feature, KEY, ATTRIBUTES, executor, tracker)

  override fun trackString(feature: Feature, executor: Executor, tracker: (String) -> Unit) =
    featureFlags.trackString(feature, KEY, ATTRIBUTES, executor, tracker)

  override fun <T : Enum<T>> trackEnum(
    feature: Feature,
    clazz: Class<T>,
    executor: Executor,
    tracker: (T) -> Unit
  ) = featureFlags.trackEnum(feature, KEY, clazz, ATTRIBUTES, executor, tracker)

  override fun <T> trackJson(
    feature: Feature,
    clazz: Class<T>,
    executor: Executor,
    tracker: (T) -> Unit
  ) = featureFlags.trackJson(feature, KEY, clazz, ATTRIBUTES, executor, tracker)
}
