package misk.environment

import misk.inject.KAbstractModule
import wisp.deployment.Deployment

/** Binds [Deployment] and [Env] to make them available to services and actions
 */
class DeploymentModule(
  private val deployment: Deployment,
  private val env: Env
) : KAbstractModule() {

  override fun configure() {
    bind<Deployment>().toInstance(deployment)
    bind<Env>().toInstance(env)
  }
}
