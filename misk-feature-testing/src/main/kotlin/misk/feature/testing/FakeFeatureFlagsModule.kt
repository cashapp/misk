package misk.feature.testing

import com.google.inject.Provider
import com.google.inject.TypeLiteral
import com.squareup.moshi.Moshi
import misk.ServiceModule
import misk.feature.DynamicConfig
import misk.feature.FeatureFlags
import misk.feature.FeatureService
import misk.feature.testing.FakeFeatureFlagsOverrideModule.FakeFeatureFlagsOverride
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.parameterizedType
import misk.inject.toKey
import misk.inject.typeLiteral
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Binds a [FakeFeatureFlags] that allows tests to override values.
 *
 * To define overrides (especially test defaults) use [FakeFeatureFlagsOverrideModule].
 * In a given misk service's test setup, there is one [FakeFeatureFlagsModule] installed and many
 * [FakeFeatureFlagsOverrideModule] installed.
 */
class FakeFeatureFlagsModule(
  private val qualifier: KClass<out Annotation>? = null
) : KAbstractModule() {
  private val overrides = mutableListOf<FakeFeatureFlagsOverrideModule>()

  override fun configure() {
    requireBinding<Moshi>()
    newMultibinder<FakeFeatureFlagsOverride>()
    overrides.forEach { install(it) }
    val key = FakeFeatureFlags::class.toKey(qualifier)
    val overridesType =
      parameterizedType<Set<*>>(FakeFeatureFlagsOverride::class.java).typeLiteral() as TypeLiteral<Set<FakeFeatureFlagsOverride>>
    val overrides = getProvider(overridesType.toKey(qualifier))
    bind(key).toProvider(object : Provider<FakeFeatureFlags> {
      @Inject private lateinit var moshi: Provider<Moshi>
      override fun get() = FakeFeatureFlags(moshi).apply {
        overrides.get().forEach { it.overrideLambda(this) }
      }
    }).asSingleton()

    bind(FeatureFlags::class.toKey(qualifier)).to(key)
    bind(FeatureService::class.toKey(qualifier)).to(key)
    bind(DynamicConfig::class.toKey(qualifier)).to(key)
    install(ServiceModule(FeatureService::class.toKey(qualifier)))
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
   *
   * For overriding in many modules see [FakeFeatureFlagsOverrideModule]
   */
  fun withOverrides(lambda: FakeFeatureFlags.() -> Unit): FakeFeatureFlagsModule {
    overrides.add(FakeFeatureFlagsOverrideModule(qualifier, lambda))
    return this
  }
}

