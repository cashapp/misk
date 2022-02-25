package misk.feature.testing

import com.google.common.util.concurrent.AbstractIdleService
import com.squareup.moshi.Moshi
import misk.feature.FeatureService
import misk.feature.Attributes
import misk.feature.DynamicConfig
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.feature.TrackerReference
import misk.feature.toMisk
import wisp.feature.BooleanFeatureFlag
import wisp.feature.DoubleFeatureFlag
import wisp.feature.EnumFeatureFlag
import wisp.feature.FeatureFlag
import wisp.feature.IntFeatureFlag
import wisp.feature.JsonFeatureFlag
import wisp.feature.StringFeatureFlag
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * In-memory test implementation of [FeatureFlags] that allows flags to be overridden.
 */
@Singleton
class FakeFeatureFlags private constructor(
  val delegate: wisp.feature.testing.FakeFeatureFlags
) : AbstractIdleService(),
  FeatureFlags,
  FeatureService,
  DynamicConfig {
  companion object {
    const val KEY = "fake_dynamic_flag"
    val defaultAttributes = Attributes()
  }

  constructor() : this(wisp.feature.testing.FakeFeatureFlags())

  @Inject
  constructor(moshi: Provider<Moshi>) : this(
    wisp.feature.testing.FakeFeatureFlags { moshi.get() }
  )

  override fun startUp() {}
  override fun shutDown() {}

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
  override fun getDouble(feature: Feature) = delegate.getDouble(feature, KEY)
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

  override fun trackBoolean(
    feature: Feature,
    executor: Executor,
    tracker: (Boolean) -> Unit
  ) = delegate.trackBoolean(feature, KEY, executor, tracker).toMisk()

  override fun trackDouble(
    feature: Feature,
    executor: Executor,
    tracker: (Double) -> Unit
  ) = delegate.trackDouble(feature, KEY, executor, tracker).toMisk()

  override fun trackInt(
    feature: Feature,
    executor: Executor,
    tracker: (Int) -> Unit
  ) = delegate.trackInt(feature, KEY, executor, tracker).toMisk()

  override fun trackString(
    feature: Feature,
    executor: Executor,
    tracker: (String) -> Unit
  ) = delegate.trackString(feature, KEY, executor, tracker).toMisk()

  override fun <T : Enum<T>> trackEnum(
    feature: Feature,
    clazz: Class<T>,
    executor: Executor,
    tracker: (T) -> Unit
  ) = delegate.trackEnum(feature, KEY, clazz, executor, tracker).toMisk()

  override fun <T> trackJson(
    feature: Feature,
    clazz: Class<T>,
    executor: Executor,
    tracker: (T) -> Unit
  ) = delegate.trackJson(feature, KEY, clazz, executor, tracker).toMisk()

  fun <T : Any, Flag : FeatureFlag<in T>> overrideAny(
    clazz: Class<out FeatureFlag<T>>,
    value: T
  ): FakeFeatureFlags {
    delegate.overrideAny(clazz, value)
    return this
  }

  fun <T : Any, Flag : FeatureFlag<in T>> overrideAny(
    clazz: Class<out FeatureFlag<T>>,
    value: T,
    matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeFeatureFlags {
    delegate.overrideAny(clazz, value, matcher)
    return this
  }

  inline fun <reified Flag : BooleanFeatureFlag> override(
    value: Boolean,
    noinline matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeFeatureFlags = overrideAny(Flag::class.java, value, matcher)

  inline fun <reified Flag : StringFeatureFlag> override(
    value: String,
    noinline matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeFeatureFlags = overrideAny(Flag::class.java, value, matcher)

  inline fun <reified Flag: IntFeatureFlag> override(
    value: Int,
    noinline matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeFeatureFlags = overrideAny(Flag::class.java, value, matcher)

  inline fun <reified Flag: DoubleFeatureFlag> override(
    value: Double,
    noinline matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeFeatureFlags = overrideAny(Flag::class.java, value, matcher)

  inline fun <reified Flag: JsonFeatureFlag<T>, T : Any> override(
    value: T,
    noinline matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeFeatureFlags = overrideAny(Flag::class.java, value, matcher)

  inline fun <reified Flag: EnumFeatureFlag<T>, T : Enum<T>> override(
    value: T,
    noinline matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeFeatureFlags = overrideAny(Flag::class.java, value, matcher)

  fun override(
    feature: Feature,
    value: Boolean
  ) = delegate.override<Boolean>(feature, value)

  fun override(
    feature: Feature,
    value: Double
  ) = delegate.override<Double>(feature, value)

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
    delegate.overrideKeyJson(feature, KEY, value, defaultAttributes)
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
    value: Double,
    attributes: Attributes = defaultAttributes
  ) = delegate.overrideKey<Double>(feature, key, value, attributes)

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
  ) = delegate.overrideKeyJson(feature, key, value, attributes)

  fun reset() {
    delegate.reset()
  }
}
