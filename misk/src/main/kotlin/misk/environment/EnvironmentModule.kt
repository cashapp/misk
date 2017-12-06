package misk.environment

import com.google.inject.AbstractModule
import misk.logging.getLogger

private val logger = getLogger<EnvironmentModule>()

class EnvironmentModule(val environment: Environment) : AbstractModule() {
  override fun configure() {
    bind(Environment::class.java).toInstance(environment)
  }

  companion object {
    @JvmStatic
    fun fromEnvironmentVariable(): EnvironmentModule {
      val environmentName = System.getenv("ENV")
      val environment = if (environmentName != null) {
        Environment.valueOf(environmentName.toUpperCase())
      } else {
        logger.warn("No ENV variable found, running in DEVELOPMENT")
        Environment.DEVELOPMENT
      }
      return EnvironmentModule(environment)
    }
  }
}
