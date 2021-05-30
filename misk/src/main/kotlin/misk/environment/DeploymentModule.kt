package misk.environment

import com.google.inject.Module
import com.google.inject.util.Modules
import misk.inject.KAbstractModule

/** Binds [Deployment], [wisp.deployment.Deployment] and [Env] to make them available to
 * services and actions
 */
class DeploymentModule(
  private val deployment: Deployment,
  private val env: Env
) : KAbstractModule() {

  constructor(
    deployment: wisp.deployment.Deployment,
    env: Env

  ) : this(
    Deployment(
      deployment.name,
      deployment.isProduction,
      deployment.isTest,
      deployment.isLocalDevelopment
    ),
    env
  )

  override fun configure() {
    bind<wisp.deployment.Deployment>().toInstance(deployment.wispDeployment)
    bind<Deployment>().toInstance(deployment)
    bind<Env>().toInstance(env)
  }

  companion object {
    /**
     * Return a Module that binds a [Deployment] and [Env] for a test environment.
     *
     * Until [Environment] is deleted, this also installs an [EnvironmentModule]
     */
    fun forTesting(): Module {
      return Modules.combine(
        DeploymentModule(
          deployment = TEST_DEPLOYMENT.wispDeployment,
          env = Env("TESTING")
        ),
        EnvironmentModule(Environment.TESTING)
      )
    }

    /**
     * A [Deployment] that can be used in tests.
     * [DeploymentModule.forTesting] will bind this instance.
     */
    val TEST_DEPLOYMENT = Deployment(name = "testing", isTest = true)
  }
}
