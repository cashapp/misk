package misk.feature.testing

import com.google.common.util.concurrent.AbstractIdleService
import com.squareup.moshi.Moshi
import misk.feature.Attributes
import misk.feature.DynamicConfig
import misk.feature.Feature
import misk.feature.FeatureFlagValidation
import misk.feature.FeatureFlags
import misk.feature.FeatureService
import misk.feature.fromSafeJson
import misk.feature.toSafeJson
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * In-memory test implementation of [FeatureFlags] that allows flags to be overridden.
 */
@Singleton
class FakeFeatureFlags @Inject constructor(private val moshi : Provider<Moshi>) : AbstractIdleService(),
    FeatureFlags,
    FeatureService,
    DynamicConfig {

  companion object {
    const val KEY = "fake_dynamic_flag"
  }

  override fun startUp() {}
  override fun shutDown() {}

  private val overrides = ConcurrentHashMap<MapKey, Any>()

  override fun getBoolean(feature: Feature, key: String, attributes: Attributes): Boolean {
    return getOrDefault(feature, key, false)
  }

  override fun getInt(feature: Feature, key: String, attributes: Attributes): Int =
      get(feature, key) as? Int ?: throw IllegalArgumentException(
          "Int flag $feature must be overridden with override() before use")

  override fun getString(feature: Feature, key: String, attributes: Attributes): String =
      get(feature, key) as? String ?: throw IllegalArgumentException(
          "String flag $feature must be overridden with override() before use")

  override fun <T : Enum<T>> getEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes
  ): T {
    @Suppress("unchecked_cast")
    return getOrDefault(feature, key, clazz.enumConstants[0]) as T
  }

  override fun <T> getJson(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes
  ): T {
    val jsonFn = get(feature, key) as? Function0<*> ?: throw IllegalArgumentException(
        "JSON flag $feature must be overridden with override() before use: ${get(feature, key)}")
    // The JSON is lazily provided to handle the case where the override is provided by the
    // FakeFeatureFlagModule and the Moshi instance cannot be accessed inside the module.
    val json = jsonFn.invoke() as? String ?: throw IllegalArgumentException(
        "JSON function did not provide a string")
    return moshi.get().adapter(clazz).fromSafeJson(json)
        ?: throw IllegalArgumentException("null value deserialized from $feature")
  }

  override fun getBoolean(feature: Feature) = getBoolean(feature, KEY)
  override fun getInt(feature: Feature) = getInt(feature, KEY)
  override fun getString(feature: Feature) = getString(feature, KEY)
  override fun <T : Enum<T>> getEnum(feature: Feature, clazz: Class<T>): T = getEnum(feature, KEY, clazz)
  override fun <T> getJson(feature: Feature, clazz: Class<T>): T = getJson(feature, KEY, clazz)

  private fun get(feature: Feature, key: String): Any? {
    FeatureFlagValidation.checkValidKey(feature, key)
    return overrides.getOrElse(MapKey(feature, key)) {
      overrides[MapKey(feature)]
    }
  }

  private fun <V> getOrDefault(feature: Feature, key: String, defaultValue: V): V {
    FeatureFlagValidation.checkValidKey(feature, key)
    @Suppress("unchecked_cast")
    return overrides.getOrElse(MapKey(feature, key)) {
      overrides.getOrDefault(MapKey(feature), defaultValue)
    } as V
  }

  fun override(feature: Feature, value: Boolean) {
    overrides[MapKey(feature)] = value
  }

  fun override(feature: Feature, value: Int) {
    overrides[MapKey(feature)] = value
  }

  fun override(feature: Feature, value: String) {
    overrides[MapKey(feature)] = value
  }

  fun override(feature: Feature, value: Enum<*>) {
    overrides[MapKey(feature)] = value
  }

  fun <T> override(feature: Feature, value: T, clazz: Class<T>) {
    overrides[MapKey(feature)] = { moshi.get().adapter(clazz).toSafeJson(value) }
  }

  fun overrideJsonString(feature: Feature, json : String) {
    overrides[MapKey(feature)] = { json }
  }

  inline fun <reified T> overrideJson(feature: Feature, value: T) {
    override(feature, value, T::class.java)
  }

  fun overrideKey(feature: Feature, key: String, value: Boolean) {
    overrides[MapKey(feature, key)] = value
  }

  fun overrideKey(feature: Feature, key: String, value: Int) {
    overrides[MapKey(feature, key)] = value
  }

  fun overrideKey(feature: Feature, key: String, value: String) {
    overrides[MapKey(feature, key)] = value
  }

  fun overrideKey(feature: Feature, key: String, value: Enum<*>) {
    overrides[MapKey(feature, key)] = value
  }

  fun <T> overrideKey(feature: Feature, key: String, value: T, clazz : Class<T>) {
    overrides[MapKey(feature, key)] = { moshi.get().adapter(clazz).toSafeJson(value) }
  }

  inline fun <reified T> overrideKeyJson(feature: Feature, key: String, value: T) {
    overrideKey(feature, key, value, T::class.java)
  }

  fun reset() {
    overrides.clear()
  }

  private data class MapKey(val feature: Feature, val key: String? = null)
}
