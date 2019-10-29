package misk.feature.testing

import com.google.common.util.concurrent.AbstractIdleService
import misk.feature.Attributes
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
    FeatureService {
  override fun startUp() {}
  override fun shutDown() {}

  private val overrides = ConcurrentHashMap<Feature, Any>()

  override fun getBoolean(feature: Feature, key: String, attributes: Attributes): Boolean {
    return overrides.getOrDefault(feature, false) as Boolean
  }

  override fun getInt(feature: Feature, key: String, attributes: Attributes): Int =
      overrides[feature] as? Int ?: throw IllegalArgumentException(
          "Int flag $feature must be overridden with override() before use")

  override fun getString(feature: Feature, key: String, attributes: Attributes): String =
      overrides[feature] as? String ?: throw IllegalArgumentException(
          "String flag $feature must be overridden with override() before use")

  override fun <T : Enum<T>> getEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes
  ): T {
    @Suppress("unchecked_cast")
    return overrides.getOrDefault(feature, clazz.enumConstants[0]) as T
  }

  fun override(feature: Feature, value: Boolean) {
    overrides[feature] = value
  }

  fun override(feature: Feature, value: Int) {
    overrides[feature] = value
  }

  fun override(feature: Feature, value: String) {
    overrides[feature] = value
  }

  fun override(feature: Feature, value: Enum<*>) {
    overrides[feature] = value
  }

  fun reset() {
    overrides.clear()
  }
}
