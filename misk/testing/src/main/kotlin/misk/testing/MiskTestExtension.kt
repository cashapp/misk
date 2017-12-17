package misk.testing

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Module
import misk.inject.uninject
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.concurrent.TimeUnit

internal class MiskTestExtension: BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        val modules = context.getActionTestModules()

        val injector = Guice.createInjector(modules)
        injector.injectMembers(context.requiredTestInstance)
        context.store("injector", injector)

        if (context.startService()) {
            // Start service
            val serviceManager = injector.getInstance(ServiceManager::class.java)
            serviceManager.startAsync()
            serviceManager.awaitHealthy(5, TimeUnit.SECONDS)
        }
    }

    override fun afterEach(context: ExtensionContext) {
        if (context.startService()) {
            // Stop service
            val injector = context.retrieve<Injector>("injector")
            val serviceManager = injector.getInstance(ServiceManager::class.java)
            serviceManager.stopAsync()
            serviceManager.awaitStopped(5, TimeUnit.SECONDS)
        }

        uninject(context.requiredTestInstance)
    }
}

private fun ExtensionContext.startService(): Boolean {
    val namespace = ExtensionContext.Namespace.create(requiredTestClass)
    // First check the context cache
    return getStore(namespace).getOrComputeIfAbsent("startService",
            { requiredTestClass.getAnnotationsByType(ActionTest::class.java)[0].startService },
            Boolean::class.java)
}

private fun <T> ExtensionContext.store(name: String, value: T) {
    val namespace = ExtensionContext.Namespace.create(requiredTestClass)
    getStore(namespace).put(name, value)
}

private inline fun <reified T> ExtensionContext.retrieve(name: String): T {
    val namespace = ExtensionContext.Namespace.create(requiredTestClass)
    return getStore(namespace)[name, T::class.java]
}

private fun ExtensionContext.getActionTestModules(): Iterable<Module> {
    val namespace = ExtensionContext.Namespace.create(requiredTestClass)
    // First check the context cache
    @Suppress("UNCHECKED_CAST")
    return getStore(namespace).getOrComputeIfAbsent("module",
            { modulesViaReflection() }) as Iterable<Module>
}

private fun ExtensionContext.modulesViaReflection(): Iterable<Module> {
    return requiredTestClass.declaredFields
            .filter { it.isAnnotationPresent(ActionTestModule::class.java) }
            .map {
                it.isAccessible = true
                it.get(requiredTestInstance) as Module
            }
}

