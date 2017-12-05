package misk

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import com.google.inject.Module

class MiskApplication(private vararg val modules: Module) {
    fun startAndAwaitStopped() {
        val injector = Guice.createInjector(
               modules.asList()
        )
        val serviceManager = injector.getInstance(ServiceManager::class.java)

        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                serviceManager.stopAsync()
                serviceManager.awaitStopped()
            }
        })

        serviceManager.startAsync()
        serviceManager.awaitHealthy()
        serviceManager.awaitStopped()
    }
}
