package misk.testing

import com.google.inject.Guice
import com.google.inject.Module
import com.sun.org.apache.xpath.internal.operations.Mod
import misk.inject.uninject
import org.junit.jupiter.api.extension.*
import org.junit.platform.commons.util.AnnotationUtils
import kotlin.reflect.full.createInstance

internal class MiskTestExtension: BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext?) {
        val modules = getModules(context!!)

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
                { _ -> createModulesFromProviders(findModuleProviders(context)) }) as Iterable<Module>
    }

    private fun findModuleProviders(context: ExtensionContext): List<ModuleProvider> {
        return context.requiredTestClass.declaredFields
                .filter { f -> f.isAnnotationPresent(Modules::class.java) }
                .map { f ->
                    f.isAccessible = true
                    f.get(context.requiredTestInstance) as ModuleProvider
                }
    }

    private fun createModulesFromProviders(providers: List<ModuleProvider>): Iterable<Module> {
        return providers.map { p -> createModulesFromProvider(p) }.flatten()
    }

    private fun createModulesFromProvider(provider: ModuleProvider): Iterable<Module> {
        val instantiatedModules = provider.moduleClassList.map { k -> k.createInstance() }
        return instantiatedModules.union(provider.moduleList)
    }
}
