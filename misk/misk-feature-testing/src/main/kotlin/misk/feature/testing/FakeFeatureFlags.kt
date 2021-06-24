package misk.feature.testing

import com.google.common.util.concurrent.AbstractIdleService
import com.squareup.moshi.Moshi
import misk.feature.FeatureService
import wisp.feature.Attributes
import wisp.feature.DynamicConfig
import wisp.feature.Feature
import wisp.feature.FeatureFlags
import wisp.feature.toSafeJson
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory test implementation of [FeatureFlags] that allows flags to be overridden.
 */
@Singleton
class FakeFeatureFlags @Inject constructor(val moshi: Moshi) : AbstractIdleService(), FeatureFlags, FeatureService, DynamicConfig {
  companion object {
    const val KEY = "fake_dynamic_flag"
    val defaultAttributes = Attributes()
  }

  private val delegate: wisp.feature.testing.FakeFeatureFlags by lazy {
    wisp.feature.testing.FakeFeatureFlags(moshi)
  }

  override fun startUp() {}
  override fun shutDown() {}

  override fun getBoolean(feature: Feature, key: String, attributes: Attributes): Boolean =
    delegate.getBoolean(feature, key, attributes)

  override fun getInt(feature: Feature, key: String, attributes: Attributes): Int =
    delegate.getInt(feature, key, attributes)

  override fun getString(feature: Feature, key: String, attributes: Attributes): String =
    delegate.getString(feature, key, attributes)

  override fun <T : Enum<T>> getEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes
  ): T =
    delegate.getEnum(feature, key, clazz, attributes)

  override fun <T> getJson(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes
  ): T {
    return delegate.getJson(feature, key, clazz, attributes)
  }

  override fun getBoolean(feature: Feature) = delegate.getBoolean(feature, KEY)
  override fun getInt(feature: Feature) = delegate.getInt(feature, KEY)
  override fun getString(feature: Feature) = delegate.getString(feature, KEY)
  override fun <T : Enum<T>> getEnum(feature: Feature, clazz: Class<T>): T = delegate.getEnum(
    feature,
    KEY,
    clazz
  )

  override fun <T> getJson(feature: Feature, clazz: Class<T>): T = delegate.getJson(
    feature,
    KEY,
    clazz
  )

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

  override fun trackBoolean(
    feature: Feature,
    executor: Executor,
    tracker: (Boolean) -> Unit
  ) = delegate.trackBoolean(feature, KEY, executor, tracker)

  override fun trackInt(
    feature: Feature,
    executor: Executor,
    tracker: (Int) -> Unit
  ) = delegate.trackInt(feature, KEY, executor, tracker)

  override fun trackString(
    feature: Feature,
    executor: Executor,
    tracker: (String) -> Unit
  ) = delegate.trackString(feature, KEY, executor, tracker)

  override fun <T : Enum<T>> trackEnum(
    feature: Feature,
    clazz: Class<T>,
    executor: Executor,
    tracker: (T) -> Unit
  ) = delegate.trackEnum(feature, KEY, clazz, executor, tracker)

  override fun <T> trackJson(
    feature: Feature,
    clazz: Class<T>,
    executor: Executor,
    tracker: (T) -> Unit
  ) = delegate.trackJson(feature, KEY, clazz, executor, tracker)

  fun override(
    feature: Feature,
    value: Boolean
  ) = delegate.override<Boolean>(feature, value)

  fun override(
    feature: Feature,
    value: Int
  ) = delegate.override<Int>(feature, value)

  fun override(
    feature: Feature,
    value: String
  ) = delegate.override<String>(feature, value)

  fun override(
    feature: Feature,
    value: Enum<*>
  ) = delegate.override<Enum<*>>(feature, value)

  fun <T> override(
    feature: Feature,
    value: T
  ) = delegate.overrideKey(feature, KEY, value, defaultAttributes)

  fun <T> override(
    feature: Feature,
    value: T,
    clazz: Class<T>
  ) {
    delegate.override(feature, value, clazz)
  }

  fun overrideJsonString(feature: Feature, json: String) {
    delegate.overrideJsonString(feature, json)
  }

  inline fun <reified T> overrideJson(feature: Feature, value: T) {
    overrideKeyJson(feature, KEY, value, defaultAttributes)
  }

  @JvmOverloads
  fun overrideKey(
    feature: Feature,
    key: String,
    value: Boolean,
    attributes: Attributes = defaultAttributes
  ) = delegate.overrideKey<Boolean>(feature, key, value, attributes)

  @JvmOverloads
  fun overrideKey(
    feature: Feature,
    key: String,
    value: Int,
    attributes: Attributes = defaultAttributes
  ) = delegate.overrideKey<Int>(feature, key, value, attributes)

  @JvmOverloads
  fun overrideKey(
    feature: Feature,
    key: String,
    value: String,
    attributes: Attributes = defaultAttributes
  ) = delegate.overrideKey<String>(feature, key, value, attributes)

  @JvmOverloads
  fun overrideKey(
    feature: Feature,
    key: String,
    value: Enum<*>,
    attributes: Attributes = defaultAttributes
  ) = delegate.overrideKey<Enum<*>>(feature, key, value, attributes)

  fun <T> overrideKey(
    feature: Feature,
    key: String,
    value: T,
    clazz: Class<T>
  ) {
    delegate.overrideKey(feature, key, value, clazz)
  }

  @JvmOverloads
  fun <T> overrideKey(
    feature: Feature,
    key: String,
    value: T,
    attributes: Attributes = defaultAttributes
  ) {
    delegate.overrideKey(feature, key, value, attributes)
  }

  @JvmOverloads
  inline fun <reified T> overrideKeyJson(
    feature: Feature,
    key: String,
    value: T,
    attributes: Attributes = defaultAttributes
  ) {
    val jsonValue = { moshi.adapter(T::class.java).toSafeJson(value) }
    overrideKey(feature, key, jsonValue, attributes)
  }

  fun reset() {
    delegate.reset()
  }

}
