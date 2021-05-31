package misk.environment

/** Deployment describes the context in which the application is running */
//
// TODO(chrisryan): "soft" deprecating
// @Deprecated(
//  message = "Use wisp.deployment.Deployment",
//  replaceWith = ReplaceWith(
//    "Deployment",
//    "wisp.deployment.Deployment"
//  )
//)
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

  val wispDeployment: wisp.deployment.Deployment
    get() = wisp.deployment.Deployment(
      name,
      isProduction = isProduction,
      isTest = isTest,
      isLocalDevelopment = isLocalDevelopment
    )
}
