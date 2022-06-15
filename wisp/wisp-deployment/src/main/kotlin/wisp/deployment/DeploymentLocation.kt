package wisp.deployment

/**
 * For information about the deployment location.
 */
interface DeploymentLocation {

    /**
     * Deployment identification, e.g. Kubernetes pod name or host name, etc.
     */
    val id: String
}
