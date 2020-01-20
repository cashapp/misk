package misk.eventrouter

import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KubernetesHealthCheck @Inject constructor() : HealthCheck {
  @Inject private lateinit var kubernetesClusterConnector: KubernetesClusterConnector

  override fun status(): HealthStatus {
    return kubernetesClusterConnector.healthStatus()
  }
}
