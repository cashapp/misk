package wisp.deployment

/**
 * Provides a deployment location id from the environment variable value.
 */
class EnvironmentVariableDeploymentLocation(
  private val environmentVariableName: String,
  private val environmentVariableLoader: EnvironmentVariableLoader = EnvironmentVariableLoader.real
) : DeploymentLocation {

  override val id: String
    get() = environmentVariableLoader.getEnvironmentVariable(environmentVariableName)
}
