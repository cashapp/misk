package misk.feature.launchdarkly

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.feature.Attributes
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.feature.FeatureService
import misk.feature.toMisk
import wisp.feature.BooleanFeatureFlag
import wisp.feature.DoubleFeatureFlag
import wisp.feature.EnumFeatureFlag
import wisp.feature.IntFeatureFlag
import wisp.feature.JsonFeatureFlag
import wisp.feature.StringFeatureFlag
import java.util.concurrent.Executor

/**
 * Implementation of [FeatureFlags] using LaunchDarkly's Java SDK.
 * See https://docs.launchdarkly.com/docs/java-sdk-reference documentation.
 */
@Singleton
class LaunchDarklyFeatureFlags @Inject constructor(
  private val delegate: wisp.launchdarkly.LaunchDarklyFeatureFlags,
) : AbstractIdleService(), FeatureFlags, FeatureService {
  override fun startUp() {
    delegate.startUp()
  }

  override fun shutDown() {
    delegate.shutDown()
  }

  override fun get(flag: BooleanFeatureFlag): Boolean = delegate.get(flag)
  override fun get(flag: StringFeatureFlag): String = delegate.get(flag)
  override fun get(flag: IntFeatureFlag): Int = delegate.get(flag)
  override fun get(flag: DoubleFeatureFlag): Double = delegate.get(flag)
  override fun <T : Enum<T>> get(flag: EnumFeatureFlag<T>): T = delegate.get(flag)
  override fun <T : Any> get(flag: JsonFeatureFlag<T>): T = delegate.get(flag)

  override fun getBoolean(feature: Feature, key: String, attributes: Attributes): Boolean =
    delegate.getBoolean(feature, key, attributes)

  override fun getDouble(feature: Feature, key: String, attributes: Attributes): Double =
    delegate.getDouble(feature, key, attributes)

  override fun getInt(feature: Feature, key: String, attributes: Attributes): Int =
    delegate.getInt(feature, key, attributes)

  override fun getString(feature: Feature, key: String, attributes: Attributes): String =
    delegate.getString(feature, key, attributes)

  override fun <T : Enum<T>> getEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes,
  ): T = delegate.getEnum(feature, key, clazz, attributes)

  override fun <T> getJson(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes,
  ): T = delegate.getJson(feature, key, clazz, attributes)

  override fun trackBoolean(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (Boolean) -> Unit,
  ) = delegate.trackBoolean(feature, key, attributes, executor, tracker).toMisk()

  override fun trackDouble(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (Double) -> Unit,
  ) = delegate.trackDouble(feature, key, attributes, executor, tracker).toMisk()

  override fun trackInt(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (Int) -> Unit,
  ) = delegate.trackInt(feature, key, attributes, executor, tracker).toMisk()

  override fun trackString(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (String) -> Unit,
  ) = delegate.trackString(feature, key, attributes, executor, tracker).toMisk()

  override fun <T : Enum<T>> trackEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes,
    executor: Executor,
    tracker: (T) -> Unit,
  ) = delegate.trackEnum(feature, key, clazz, attributes, executor, tracker).toMisk()

  override fun <T> trackJson(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes,
    executor: Executor,
    tracker: (T) -> Unit,
  ) = delegate.trackJson(feature, key, clazz, attributes, executor, tracker).toMisk()
}
