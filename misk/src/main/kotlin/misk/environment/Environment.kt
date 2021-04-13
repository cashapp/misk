package misk.environment

import com.google.common.base.Preconditions
import wisp.logging.getLogger

/** The environment in which the application is running */
@Deprecated("use Deployment instead")
enum class Environment {
  TESTING,
  DEVELOPMENT,
  STAGING,
  PRODUCTION;

  fun isFake(): Boolean = this == TESTING || this == DEVELOPMENT

  fun isReal(): Boolean = !isFake()

  companion object {
    internal val logger = getLogger<Environment>()
    private const val ENV_ENVIRONMENT = "ENVIRONMENT"

    private lateinit var rawEnv: String

    fun setTesting() {
      if (::rawEnv.isInitialized) {
        Preconditions.checkState(rawEnv == TESTING.name, "Environment already set to $rawEnv")
      }
      rawEnv = TESTING.name
    }

    @JvmStatic
    fun fromEnvironmentVariable(): Environment {
      val rawEnvName = when (val rawEnv = rawEnvironment()) {
        "PLATFORM-STAGING", "PLAT-OPS-STAGING" -> STAGING.name
        "PLAT-OPS-PRODUCTION" -> PRODUCTION.name
        // ^^^ Special cases during deprecation of Environment
        else -> rawEnv
      }
      return valueOf(rawEnvName)
    }

    fun rawEnvironment(): String {
      // The system variable should always take precedence
      val environmentName = System.getenv(ENV_ENVIRONMENT)
      val environment = when (environmentName) {
        null -> {
          if (::rawEnv.isInitialized) {
            rawEnv
          } else {
            // TODO(dhanji): We should remove this default, eventually
            logger.warn {
              "No environment variable with key $ENV_ENVIRONMENT found, running in DEVELOPMENT"
            }
            DEVELOPMENT.toString()
          }
        }
        else -> environmentName
      }

      logger.info { "Running with environment $environment" }
      return environment
    }
  }
}
