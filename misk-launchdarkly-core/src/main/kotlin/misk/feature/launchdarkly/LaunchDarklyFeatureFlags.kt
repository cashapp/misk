package misk.feature.launchdarkly

import com.google.common.util.concurrent.AbstractIdleService
import com.launchdarkly.sdk.EvaluationDetail
import com.launchdarkly.sdk.EvaluationReason
import com.launchdarkly.sdk.LDUser
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.server.interfaces.LDClientInterface
import com.launchdarkly.shaded.com.google.common.base.Preconditions.checkState
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import wisp.feature.FeatureFlagValidation
import misk.feature.FeatureService
import mu.KotlinLogging
import misk.feature.Attributes
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.feature.TrackerReference
import misk.feature.toMisk
import wisp.feature.BooleanFeatureFlag
import wisp.feature.DoubleFeatureFlag
import wisp.feature.EnumFeatureFlag
import wisp.feature.IntFeatureFlag
import wisp.feature.JsonFeatureFlag
import wisp.feature.StringFeatureFlag
import wisp.feature.fromSafeJson
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [FeatureFlags] using LaunchDarkly's Java SDK.
 * See https://docs.launchdarkly.com/docs/java-sdk-reference documentation.
 */
@Singleton
class LaunchDarklyFeatureFlags private constructor (
  private val delegate: wisp.launchdarkly.LaunchDarklyFeatureFlags
) : AbstractIdleService(), FeatureFlags, FeatureService {
  @Inject
  constructor(
    ldClient: LDClientInterface,
    moshi: Moshi
  ) : this(wisp.launchdarkly.LaunchDarklyFeatureFlags(ldClient, moshi))

  override fun startUp() { delegate.startUp() }
  override fun shutDown() { delegate.shutDown() }

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
    attributes: Attributes
  ): T = delegate.getEnum(feature, key, clazz, attributes)

  override fun <T> getJson(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes
  ): T = delegate.getJson(feature, key, clazz, attributes)

  override fun trackBoolean(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (Boolean) -> Unit
  ) = delegate.trackBoolean(feature, key, attributes, executor, tracker).toMisk()

  override fun trackDouble(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (Double) -> Unit
  ) = delegate.trackDouble(feature, key, attributes, executor, tracker).toMisk()

  override fun trackInt(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (Int) -> Unit
  ) = delegate.trackInt(feature, key, attributes, executor, tracker).toMisk()

  override fun trackString(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (String) -> Unit
  ) = delegate.trackString(feature, key, attributes, executor, tracker).toMisk()

  override fun <T : Enum<T>> trackEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes,
    executor: Executor,
    tracker: (T) -> Unit
  ) = delegate.trackEnum(feature, key, clazz, attributes, executor, tracker).toMisk()

  override fun <T> trackJson(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes,
    executor: Executor,
    tracker: (T) -> Unit
  ) = delegate.trackJson(feature, key, clazz, attributes, executor, tracker).toMisk()
}
