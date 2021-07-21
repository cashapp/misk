package misk.feature.testing

import com.squareup.moshi.Moshi
import misk.ServiceModule
import misk.feature.DynamicConfig
import misk.feature.FeatureFlags
import misk.feature.FeatureService
import misk.inject.KAbstractModule
import misk.inject.toKey
import kotlin.reflect.KClass

/**
 * Binds a [FakeFeatureFlags] that allows tests to override values.
 */
class FakeFeatureFlagsModule(
  private val qualifier: KClass<out Annotation>? = null
) : KAbstractModule() {
  private val overrides = mutableListOf<FakeFeatureFlags.() -> Unit>()

  override fun configure() {
    requireBinding<Moshi>()
    val testFeatureFlags = FakeFeatureFlags(getProvider(Moshi::class.java))
    val key = FakeFeatureFlags::class.toKey(qualifier)
    bind(key).toInstance(testFeatureFlags)
    bind(FeatureFlags::class.toKey(qualifier)).to(key)
    bind(wisp.feature.FeatureFlags::class.toKey(qualifier)).to(key)
    bind(FeatureService::class.toKey(qualifier)).to(key)
    bind(DynamicConfig::class.toKey(qualifier)).to(key)
    bind(wisp.feature.DynamicConfig::class.toKey(qualifier)).to(key)
    install(ServiceModule(FeatureService::class.toKey(qualifier)))
    overrides.forEach {
      it.invoke(testFeatureFlags)
    }
  }

  /**
   * Add overrides for the feature flags. Allows flags to be overridden at module instantiation
   * instead of within individual test classes.
   *
   * Usage:
   * ```
   * install(FakeFeatureFlagsModule().withOverrides {
   *   override(Feature("foo"), true)
   * })
   * ```
   */
  fun withOverrides(lambda: FakeFeatureFlags.() -> Unit): FakeFeatureFlagsModule {
    overrides.add(lambda)
    return this
  }
}
