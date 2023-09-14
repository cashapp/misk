package misk.aws.dynamodb.testing

import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus

val healthyHealthCheck = object : HealthCheck {
  override fun status() = HealthStatus.healthy()
}

val unhealthyHealthCheck = object : HealthCheck {
  override fun status() = HealthStatus.unhealthy()
}
