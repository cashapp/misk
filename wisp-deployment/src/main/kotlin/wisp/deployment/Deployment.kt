package wisp.deployment

/** Deployment describes the context in which the application is running
 * TODO: convert to data class when misk Deployment removed.
 */
open class Deployment(
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
   * Whether the service is running in a staging environment.
   */
  val isStaging: Boolean = false,

  /**
   * Whether the service is running in a test environment, either locally or in a CI.
   */
  val isTest: Boolean = false,

  /**
   * Whether the service is running on a local developer machine, including as a Docker image.
   */
  val isLocalDevelopment: Boolean = false
) {

  val delegateDeployment =
    DelegateDeployment(
      name = name,
      isProduction = isProduction,
      isStaging = isStaging,
      isTest = isTest,
      isLocalDevelopment = isLocalDevelopment
    )

  /**
   * Returns true if running in a managed cluster, such as a staging or production cluster. Mutually exclusive with isFake.
   */
  val isReal: Boolean
    get() = delegateDeployment.isReal

  /**
   * Returns true if running outside of a cluster (CI or local development). Mutually exclusive with isReal.
   */
  val isFake: Boolean
    get() = delegateDeployment.isFake

  /**
   * TEMPORARY, move into [Deployment] when misk's Deployment is removed.
   */
  data class DelegateDeployment(
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
     * Whether the service is running in a staging environment.
     */
    val isStaging: Boolean = false,

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
      when {
        isProduction -> check(!isStaging && !isTest && !isLocalDevelopment)
        isStaging -> check(!isProduction && !isTest && !isLocalDevelopment)
        isTest -> check(!isProduction && !isStaging && !isLocalDevelopment)
        isLocalDevelopment -> check(!isProduction && !isStaging && !isTest)
      }
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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Deployment) return false

    if (delegateDeployment != other.delegateDeployment) return false

    return true
  }

  override fun hashCode(): Int {
    return delegateDeployment.hashCode()
  }

}

val PRODUCTION = Deployment(
  "production",
  isProduction = true,
  isStaging = false,
  isTest = false,
  isLocalDevelopment = false
)

val STAGING = Deployment(
  "staging",
  isProduction = false,
  isStaging = true,
  isTest = false,
  isLocalDevelopment = false
)

val TESTING = Deployment(
  "testing",
  isProduction = false,
  isStaging = false,
  isTest = true,
  isLocalDevelopment = false
)

val DEVELOPMENT = Deployment(
  "development",
  isProduction = false,
  isStaging = false,
  isTest = false,
  isLocalDevelopment = true
)

val deployments = mapOf(
  "production" to PRODUCTION,
  "staging" to STAGING,
  "testing" to TESTING,
  "test" to TESTING,
  "development" to DEVELOPMENT,
  "dev" to DEVELOPMENT
)

/**
 * Determines a Deployment based on the value within the ENVIRONMENT variable, defaulting to
 * local development if not set (i.e. isLocalDevelopment == true)
 */
fun getDeploymentFromEnvironmentVariable(
  /** The default environment if ENVIRONMENT is not set */
  defaultDeploymentName: String = "development",

  /** Environment Variable loader, use the real version if none supplied */
  environmentVariableLoader: EnvironmentVariableLoader = EnvironmentVariableLoader.real

): Deployment {

  val environment = environmentVariableLoader.getEnvironmentVariableOrDefault(
    "ENVIRONMENT",
    defaultDeploymentName
  )

  return deployments[environment.toLowerCase()] ?: DEVELOPMENT
}
