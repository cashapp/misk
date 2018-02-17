package misk.healthchecks

import misk.inject.KAbstractModule

class HealthChecksModule(
    private vararg val healthCheckClasses: Class<out HealthCheck>
) : KAbstractModule() {
  override fun configure() {
    val setBinder = newSetBinder<HealthCheck>()
    for (healthCheckClass in healthCheckClasses) {
      setBinder.addBinding()
          .to(healthCheckClass)
    }
  }
}
