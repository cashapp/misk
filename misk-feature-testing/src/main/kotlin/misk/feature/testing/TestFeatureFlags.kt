package misk.feature.testing

import com.google.common.util.concurrent.AbstractIdleService
import misk.feature.Attrs
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.feature.FeatureService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory test implementation of [FeatureFlags] that allows flags to be overridden.
 */
@Singleton
class TestFeatureFlags @Inject constructor() : AbstractIdleService(),
    FeatureFlags,
    FeatureService {
  override fun startUp() {}
  override fun shutDown() {}

  private val overrides = mutableMapOf<Feature, Any>()

  override fun getBool(feature: Feature, token: String, attrs: Attrs?): Boolean {
    return overrides.getOrDefault(feature, false) as Boolean
  }

  override fun getInt(feature: Feature, token: String, attrs: Attrs?): Int {
    checkNotNull(
        overrides[feature]) { "Int flag $feature must be overridden with override() before use" }
    return overrides[feature] as Int
  }

  override fun getString(feature: Feature, token: String, attrs: Attrs?): String {
    checkNotNull(
        overrides[feature]) { "String flag $feature must be overridden with override() before use" }
    return overrides[feature] as String
  }

  override fun <T : Enum<T>> getEnum(
    feature: Feature,
    token: String,
    clazz: Class<T>,
    attrs: Attrs?
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
