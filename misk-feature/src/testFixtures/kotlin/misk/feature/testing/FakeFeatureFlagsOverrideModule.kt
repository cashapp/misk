package misk.feature.testing

import misk.inject.KAbstractModule
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
