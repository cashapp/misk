package misk.environment

import misk.inject.KAbstractModule
import wisp.deployment.Deployment
import wisp.deployment.getDeploymentFromEnvironmentVariable

/** Binds [Deployment] to make it available to services and actions
 */
class DeploymentModule(
  private val deployment: Deployment = getDeploymentFromEnvironmentVariable()
) : KAbstractModule() {

  override fun configure() {
    bind<Deployment>().toInstance(deployment)
  }
}
