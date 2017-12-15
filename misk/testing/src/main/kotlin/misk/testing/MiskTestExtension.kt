package misk.testing

import com.google.inject.Guice
import com.google.inject.Module
import misk.inject.uninject
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

internal class MiskTestExtension: BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        val modules = getModules(context)

        val injector = Guice.createInjector(modules)
        injector.injectMembers(context.requiredTestInstance)
    }

    override fun afterEach(context: ExtensionContext?) {
        uninject(context!!.requiredTestInstance)
    }

    private fun getModules(context: ExtensionContext): Iterable<Module> {
        val namespace = ExtensionContext.Namespace.create(context.requiredTestClass)
        // First check the context cache
        @Suppress("UNCHECKED_CAST")
        return context.getStore(namespace).getOrComputeIfAbsent("modules",
                { _ -> modulesFromProviders(context) }) as Iterable<Module>
    }

    private fun modulesFromProviders(context: ExtensionContext): Iterable<Module> {
        return context.requiredTestClass.declaredFields
                .filter { f -> f.isAnnotationPresent(Modules::class.java) }
                .map { f ->
                    f.isAccessible = true
                    val p = f.get(context.requiredTestInstance) as ModuleProvider
                    p.modules
                }
                .flatten()
    }
}
