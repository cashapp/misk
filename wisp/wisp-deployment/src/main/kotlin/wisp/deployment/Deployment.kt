package wisp.deployment

/** Deployment describes the context in which the application is running */
data class Deployment(
  /**
   * The name of this deployment. This is used for debugging and should not be parsed.
   *
   * All pods in the same deployment will have this same name.
   */
  val name: String,

  /**
   * Whether the service is running in a production environment, having an SLA or handling customer data.
   */
  val isProduction: Boolean = false,

  /**
   * Whether the service is running in a test environment, either locally or in a CI.
   */
  val isTest: Boolean = false,

  /**
   * Whether the service is running on a local developer machine, including as a Docker image.
   */
  val isLocalDevelopment: Boolean = false
) {
  init {
    if (isProduction) check(!isTest && !isLocalDevelopment)
    if (isTest) check(!isLocalDevelopment)
  }

  /**
   * Returns true if running in a managed cluster, such as a staging or production cluster. Mutually exclusive with isFake.
   */
  val isReal: Boolean
    get() = !isFake

  /**
   * Returns true if running outside of a cluster (CI or local development). Mutually exclusive with isReal.
   */
  val isFake: Boolean
    get() = isTest || isLocalDevelopment
}

/**
 * Determines a Deployment based on the value within the ENVIRONMENT variable, defaulting to
 * local development if not set (i.e. isLocalDevelopment == true)
 */
inline fun getDeploymentFromEnvironmentVariable(

  /** The name for the deployment, if not supplied, use the environment name */
  name: String? = null,

  /** The default environment if ENVIRONMENT is not set */
  defaultDeploymentName: String = "development"

): Deployment {

  val environment = System.getenv("ENVIRONMENT") ?: defaultDeploymentName
  val deploymentName = name ?: environment

  return when (environment.toLowerCase()) {
    "staging", "production" -> Deployment(
      deploymentName,
      isProduction = true,
      isTest = false,
      isLocalDevelopment = false
    )

    "testing", "test" -> Deployment(
      deploymentName,
      isProduction = false,
      isTest = true,
      isLocalDevelopment = false
    )

    "development" -> Deployment(
      deploymentName,
      isProduction = false,
      isTest = false,
      isLocalDevelopment = true
    )

    else -> Deployment(
      deploymentName,
      isProduction = false,
      isTest = false,
      isLocalDevelopment = true
    )
  }
}
