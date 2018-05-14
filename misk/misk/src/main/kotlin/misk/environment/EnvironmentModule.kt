package misk.environment

import misk.inject.KAbstractModule

/**
 * Binds [Environment] and [InstanceMetadata] to make both available to services and actions
 */
class EnvironmentModule(
  private val environment: Environment,
  private val instanceMetadata: InstanceMetadata? = null
) : KAbstractModule() {
  override fun configure() {
    val instanceMetadata = instanceMetadata ?: InstanceMetadata.fromEnvironmentVariables()
    bind<InstanceMetadata>().toInstance(instanceMetadata)
    bind<Environment>().toInstance(environment)
  }
}
