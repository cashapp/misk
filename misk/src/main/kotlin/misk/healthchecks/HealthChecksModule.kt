package misk.healthchecks

import misk.inject.KAbstractModule

class HealthChecksModule(
  private vararg val healthCheckClasses: Class<out HealthCheck>
) : KAbstractModule() {
  override fun configure() {
    // Always register binding for the list, even if there are no healthchecks
    newMultibinder<HealthCheck>()

    for (healthCheckClass in healthCheckClasses) {
      multibind<HealthCheck>().to(healthCheckClass)
    }
  }
}
