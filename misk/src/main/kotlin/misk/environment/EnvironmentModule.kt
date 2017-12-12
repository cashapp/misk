package misk.environment

import misk.inject.KAbstractModule
import misk.logging.getLogger

private val logger = getLogger<EnvironmentModule>()

class EnvironmentModule(val environment: Environment) : KAbstractModule() {
  override fun configure() {
    bind<Environment>().toInstance(environment)
  }

  companion object {
    private val environmentKey = "ENVIRONMENT"

    @JvmStatic
    fun fromEnvironmentVariable(): EnvironmentModule {
      val environmentName = System.getenv(environmentKey)
      val environment = if (environmentName != null) {
        Environment.valueOf(environmentName)
      } else {
        logger.warn { "No environment variable with key $environmentKey found, running in DEVELOPMENT" }
        Environment.DEVELOPMENT
      }
      logger.info { "Running with environment ${environment.name}" }
      return EnvironmentModule(environment)
    }
  }
}
