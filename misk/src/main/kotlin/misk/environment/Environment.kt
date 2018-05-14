package misk.environment

import misk.logging.getLogger


/** The environment in which the application is running */
enum class Environment {
  TESTING,
  DEVELOPMENT,
  STAGING,
  PRODUCTION;

  companion object {
    private val logger = getLogger<Environment>()
    private const val ENV_ENVIRONMENT = "ENVIRONMENT"

    @JvmStatic
    fun fromEnvironmentVariable(): Environment {
      val environmentName = System.getenv(ENV_ENVIRONMENT)
      val environment = environmentName?.let { Environment.valueOf(it) } ?: {
        logger.warn { "No environment variable with key $ENV_ENVIRONMENT found, running in DEVELOPMENT" }
        Environment.DEVELOPMENT
      }()

      logger.info { "Running with environment ${environment.name}" }
      return environment
    }
  }
}
