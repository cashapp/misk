package misk.policy.opa

import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.policy.opa.LocalOpaService.Companion.DEFAULT_POLICY_DIRECTORY

class OpaDevelopmentModule(
  private val policyDirectory: String = DEFAULT_POLICY_DIRECTORY,
  private val withLogging: Boolean = false,
  private val preferredImageVersion: String = "latest-debug"
) : KAbstractModule() {
  constructor(
    policyDirectory: String = DEFAULT_POLICY_DIRECTORY,
    withLogging: Boolean = false
  ) : this(policyDirectory, withLogging, "latest-debug")

  override fun configure() {
    install(ServiceModule<LocalOpaService>())
    bind(keyOf<LocalOpaService>()).toInstance(LocalOpaService(policyDirectory, withLogging, preferredImageVersion))
  }
}
