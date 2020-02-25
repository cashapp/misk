package misk.environment

import com.google.common.base.Preconditions
import misk.logging.getLogger

/** The environment in which the application is running */
enum class Environment {
  TESTING,
  DEVELOPMENT,
  PLATFORM_STAGING,
  STAGING,
  PRODUCTION;

  companion object {
    internal val logger = getLogger<Environment>()
    private const val ENV_ENVIRONMENT = "ENVIRONMENT"
    private lateinit var env: Environment

    fun setTesting() {
      if (::env.isInitialized) {
        Preconditions.checkState(env == TESTING, "Environment already set to ${env.name}")
      }
      env = TESTING
    }

    @JvmStatic
    fun fromEnvironmentVariable(): Environment {
      // The system variable should always take precedence
      val environmentName = System.getenv(ENV_ENVIRONMENT)

      val environment = environmentName?.let { it.replace("-", "_") }?.let { valueOf(it) } ?: {
        if (::env.isInitialized) {
          env
        } else {
          // TODO(dhanji): We should remove this default, eventually
          logger.warn { "No environment variable with key $ENV_ENVIRONMENT found, running in DEVELOPMENT" }
          Environment.DEVELOPMENT
        }
      }()

      logger.info { "Running with environment ${environment.name}" }
      return environment
    }
  }
}
