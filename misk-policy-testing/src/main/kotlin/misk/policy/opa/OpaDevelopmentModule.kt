package misk.policy.opa

import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.policy.opa.LocalOpaService.Companion.DEFAULT_POLICY_DIRECTORY

@Deprecated("Replace the dependency on misk-policy-testing with testFixtures(misk-policy)")
class OpaDevelopmentModule @JvmOverloads constructor(
  private val policyDirectory: String = DEFAULT_POLICY_DIRECTORY,
  private val withLogging: Boolean = false,
  private val preferredImageVersion: String = "latest-debug"
) : KAbstractModule() {

  override fun configure() {
    install(ServiceModule<LocalOpaService>())
    bind(keyOf<LocalOpaService>()).toInstance(LocalOpaService(policyDirectory, withLogging, preferredImageVersion))
  }
}
