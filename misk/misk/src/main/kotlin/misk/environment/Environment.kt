package misk.environment

import misk.logging.getLogger

private val logger = getLogger<Environment>()

enum class Environment {
  TESTING,
  DEVELOPMENT,
  STAGING,
  PRODUCTION;

  companion object {
    private val environmentKey = "ENVIRONMENT"

    @JvmStatic
    fun fromEnvironmentVariable(): Environment {
      val environmentName = System.getenv(environmentKey)
      val environment = if (environmentName != null) {
        Environment.valueOf(environmentName)
      } else {
        logger.warn { "No environment variable with key $environmentKey found, running in DEVELOPMENT" }
        Environment.DEVELOPMENT
      }
      logger.info { "Running with environment ${environment.name}" }
      return environment
    }
  }
}
