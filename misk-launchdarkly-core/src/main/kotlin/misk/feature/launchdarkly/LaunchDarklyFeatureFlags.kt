package misk.feature.launchdarkly

import com.google.common.util.concurrent.AbstractIdleService
import com.launchdarkly.sdk.server.interfaces.LDClientInterface
import com.squareup.moshi.Moshi
import misk.feature.FeatureService
import wisp.feature.Attributes
import wisp.feature.Feature
import wisp.feature.FeatureFlags
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [FeatureFlags] using LaunchDarkly's Java SDK.
 * See https://docs.launchdarkly.com/docs/java-sdk-reference documentation.
 */
@Singleton
class LaunchDarklyFeatureFlags @Inject constructor(
  private val ldClient: LDClientInterface,
  private val moshi: Moshi
) : AbstractIdleService(), FeatureFlags, FeatureService {

  private val delegate = wisp.launchdarkly.LaunchDarklyFeatureFlags(ldClient, moshi)

  override fun startUp() {
    delegate.startUp()
  }

  override fun shutDown() {
    delegate.shutDown()
  }

  override fun getBoolean(feature: Feature, key: String, attributes: Attributes): Boolean {
    return delegate.getBoolean(feature, key, attributes)
  }

  override fun getInt(feature: Feature, key: String, attributes: Attributes): Int {
    return delegate.getInt(feature, key, attributes)
  }

  override fun getString(feature: Feature, key: String, attributes: Attributes): String {
    return delegate.getString(feature, key, attributes)
  }

  override fun <T : Enum<T>> getEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes
  ): T {
    return delegate.getEnum(feature, key, clazz, attributes)
  }

  override fun <T> getJson(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes
  ): T {
    return delegate.getJson(feature, key, clazz, attributes)
  }

  override fun trackBoolean(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (Boolean) -> Unit
  ) = delegate.trackBoolean(feature, key, attributes, executor, tracker)

  override fun trackInt(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (Int) -> Unit
  ) = delegate.trackInt(feature, key, attributes, executor, tracker)

  override fun trackString(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (String) -> Unit
  ) = delegate.trackString(feature, key, attributes, executor, tracker)

  override fun <T : Enum<T>> trackEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes,
    executor: Executor,
    tracker: (T) -> Unit
  ) = delegate.trackEnum(feature, key, clazz, attributes, executor, tracker)

  override fun <T> trackJson(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes,
    executor: Executor,
    tracker: (T) -> Unit
  ) = delegate.trackJson(feature, key, clazz, attributes, executor, tracker)
}
