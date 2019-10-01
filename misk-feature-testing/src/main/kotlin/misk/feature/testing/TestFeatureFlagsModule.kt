package misk.feature.testing

import misk.feature.FeatureFlags
import misk.feature.FeatureService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.toKey
import kotlin.reflect.KClass

/**
 * Binds a [TestFeatureFlags] that allows tests to override values.
 */
class TestFeatureFlagsModule(
  private val qualifier: KClass<out Annotation>? = null
) : KAbstractModule() {
  private val testFeatureFlags = TestFeatureFlags()

  override fun configure() {
    val key = TestFeatureFlags::class.toKey(qualifier)
    bind(key).toInstance(testFeatureFlags)
    bind<FeatureFlags>().to(key)
    bind<FeatureService>().to(key)
    install(ServiceModule(FeatureService::class.toKey(qualifier)))
  }

  /**
   * Add overrides for the feature flags. Allows flags to be overridden at module instantiation
   * instead of within individual test classes.
   *
   * Usage:
   * ```
   * install(TestFeatureFlagsModule().withOverrides { flags ->
   *   flags.overrideBool(Feature("foo"), true)
   * })
   * ```
   */
  fun withOverrides(lambda: (TestFeatureFlags) -> Unit): TestFeatureFlagsModule {
    lambda(testFeatureFlags)
    return this
  }
}
