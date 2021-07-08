package misk.policy.opa

import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.policy.opa.LocalOpaService.Companion.DEFAULT_POLICY_DIRECTORY

class OpaDevelopmentModule(
  private val policyDirectory: String = DEFAULT_POLICY_DIRECTORY,
  private val withLogging: Boolean = false
) : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<LocalOpaService>())
    bind(keyOf<LocalOpaService>()).toInstance(LocalOpaService(policyDirectory, withLogging))
  }
}
