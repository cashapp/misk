package wisp.deployment

class FakeDeploymentLocation(private val deploymentLocation: String = "FakeDeploymentLocation") :
  DeploymentLocation {
  /**
   * Deployment identification, e.g. Kubernetes pod name or host name, etc.
   */
  override val id: String
    get() = deploymentLocation
}
