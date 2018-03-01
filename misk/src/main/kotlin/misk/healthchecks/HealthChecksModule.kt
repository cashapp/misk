package misk.healthchecks

import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.inject.newMultibinder

class HealthChecksModule(
  private vararg val healthCheckClasses: Class<out HealthCheck>
) : KAbstractModule() {
  override fun configure() {
    // Always register binding for the list, even if there are no healthchecks
    binder().newMultibinder<HealthCheck>()

    for (healthCheckClass in healthCheckClasses) {
      binder().addMultibinderBinding<HealthCheck>().to(healthCheckClass)
    }
  }
}
