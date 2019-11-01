package misk.feature.testing

import com.google.common.util.concurrent.AbstractIdleService
import misk.feature.Attributes
import misk.feature.DynamicConfig
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.feature.FeatureService
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory test implementation of [FeatureFlags] that allows flags to be overridden.
 */
@Singleton
class FakeFeatureFlags @Inject constructor() : AbstractIdleService(),
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

  override fun getBoolean(feature: Feature) = getBoolean(feature, KEY)
  override fun getInt(feature: Feature) = getInt(feature, KEY)
  override fun getString(feature: Feature) = getString(feature, KEY)
  override fun <T : Enum<T>> getEnum(feature: Feature, clazz: Class<T>): T = getEnum(feature, KEY, clazz, Attributes())

  private fun get(feature: Feature, key: String): Any? {
    return overrides.getOrElse(MapKey(feature, key)) {
      overrides[MapKey(feature)]
    }
  }

  private fun <V> getOrDefault(feature: Feature, key: String, defaultValue: V): V {
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

  fun reset() {
    overrides.clear()
  }

  private data class MapKey(val feature: Feature, val key: String? = null)
}
