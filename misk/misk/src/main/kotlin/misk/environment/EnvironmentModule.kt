package misk.environment

import misk.inject.KAbstractModule

/** Binds [Environment] to make it available to services and actions */
class EnvironmentModule(private val environment: Environment) : KAbstractModule() {
  override fun configure() {
    bind<Environment>().toInstance(environment)
  }
}
