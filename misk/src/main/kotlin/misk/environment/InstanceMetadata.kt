package misk.environment

import misk.logging.getLogger

/** Metadata about the running instance */
data class InstanceMetadata(
  val instanceName: String,
  val zone: String,
  val region: String
) {
  companion object {
    private const val ENV_INSTANCE_NAME = "HOSTNAME"
    private const val ENV_ZONE = "MY_ZONE"
    private const val ENV_REGION = "MY_REGION"

    private val logger = getLogger<InstanceMetadata>()

    private fun getEnv(envVar: String, default: String): String {
      return System.getenv(ENV_INSTANCE_NAME) ?: {
        logger.warn { "no environment variable with key $envVar; defaulting to $default" }
        default
      }()
    }

    @JvmStatic
    fun fromEnvironmentVariables(): InstanceMetadata {
      val instanceName = getEnv(ENV_INSTANCE_NAME, "dev-node")
      val zone = getEnv(ENV_ZONE, "dev-zone")
      val region = getEnv(ENV_REGION, "dev-region")
      return InstanceMetadata(instanceName, zone, region)
    }
  }
}
