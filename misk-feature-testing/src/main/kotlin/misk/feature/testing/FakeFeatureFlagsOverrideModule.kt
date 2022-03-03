package misk.feature.testing

import misk.inject.KAbstractModule

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
  private val override: FakeFeatureFlagsOverride,
) : KAbstractModule() {

  constructor(overrideLambda: FakeFeatureFlags.() -> Unit) : this(
    FakeFeatureFlagsOverride(overrideLambda)
  )

  override fun configure() {
    multibind<FakeFeatureFlagsOverride>().toInstance(override)
  }

  class FakeFeatureFlagsOverride(
    val overrideLambda: FakeFeatureFlags.() -> Unit
  )
}
