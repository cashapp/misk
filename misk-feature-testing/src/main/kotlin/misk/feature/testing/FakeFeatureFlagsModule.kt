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
@Deprecated("Replace the dependency on misk-feature-testing with testFixtures(misk-feature)")
class FakeFeatureFlagsModule(
  private val qualifier: KClass<out Annotation>? = null
) : KAbstractModule() {
  private val overrides = mutableListOf<FakeFeatureFlagsOverrideModule>()

  override fun configure() {
    requireBinding<Moshi>()

    val wispFakeFeatureFlagsKey = wisp.feature.testing.FakeFeatureFlags::class.toKey(qualifier)
    bind(wispFakeFeatureFlagsKey).toProvider(
      object : Provider<wisp.feature.testing.FakeFeatureFlags> {
        @Inject private lateinit var moshi: Provider<Moshi>
        override fun get(): wisp.feature.testing.FakeFeatureFlags =
          wisp.feature.testing.FakeFeatureFlags(moshi.get())
      }
    ).asSingleton()
    bind(wisp.feature.FeatureFlags::class.toKey(qualifier)).to(wispFakeFeatureFlagsKey)
    val wispFakeFeatureFlags = getProvider(wispFakeFeatureFlagsKey)

    newMultibinder<FakeFeatureFlagsOverride>()
    overrides.forEach { install(it) }

    val key = FakeFeatureFlags::class.toKey(qualifier)
    val overridesType =
      parameterizedType<Set<*>>(FakeFeatureFlagsOverride::class.java).typeLiteral() as TypeLiteral<Set<FakeFeatureFlagsOverride>>
    val overrides = getProvider(overridesType.toKey(qualifier))
    bind(key).toProvider {
      FakeFeatureFlags(wispFakeFeatureFlags.get()).apply {
        overrides.get().forEach { it.overrideLambda(this) }
      }
    }.asSingleton()

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

