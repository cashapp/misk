package misk.feature.testing

import com.google.inject.Provides
import misk.inject.KAbstractModule
import misk.inject.KInstallOnceModule
import misk.testing.TestFixture
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Install defaults for [FakeFeatureFlags]. This module can be install many times, allowing for
 * feature flag overrides to be modular and scoped to the module the flag is used in.
 *
 * In any module use:
 * ```
 * install(FakeFeatureFlagsModuleOverrideModule {
 *   override(Feature("foo"), true)
 *   overrideJsonString(Feature("bar"), "{ \"target\": 0.1 }")
 * })
 * ```
 */
class FakeFeatureFlagsOverrideModule private constructor(
  private val qualifier: KClass<out Annotation>? = null,
  private val override: FakeFeatureFlagsOverride,
) : KAbstractModule() {

  constructor(
    qualifier: KClass<out Annotation>? = null,
    overrideLambda: FakeFeatureFlags.() -> Unit,
  ) : this(
    qualifier,
    FakeFeatureFlagsOverride(overrideLambda)
  )

  override fun configure() {
    multibind<FakeFeatureFlagsOverride>(qualifier).toInstance(override)
  }

  class FakeFeatureFlagsOverride(
    val overrideLambda: FakeFeatureFlags.() -> Unit
  )
}

internal class FakeFeatureFlagsTestFixtureModule: KInstallOnceModule() {

  override fun configure() {
    multibind<TestFixture>().to<FakeFeatureFlagsFixture>()
  }

  @Provides
  @Singleton
  internal fun providesFakeFeatureFlagsResource(
    featureFlags: FakeFeatureFlags,
    overrides: Set<FakeFeatureFlagsOverrideModule.FakeFeatureFlagsOverride>
  ): FakeFeatureFlagsFixture {
    return FakeFeatureFlagsFixture(featureFlags, block = { overrides.forEach { it.overrideLambda(this) } })
  }
}

/**
 * Applies the default feature flag overrides before every test.
 */
internal class FakeFeatureFlagsFixture(
  private val featureFlags: FakeFeatureFlags,
  private val block: (FakeFeatureFlags.() -> Unit)
) : TestFixture {

  override fun reset() {
    block(featureFlags)
  }
}
