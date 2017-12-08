package misk.healthchecks

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder

class HealthChecksModule(
    private vararg val healthCheckClasses: Class<out HealthCheck>
) : AbstractModule() {
    override fun configure() {
        val setBinder = Multibinder.newSetBinder(binder(), HealthCheck::class.java)
        for (healthCheckClass in healthCheckClasses) {
            setBinder.addBinding().to(healthCheckClass)
        }
    }
}
