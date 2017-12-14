package misk.testing

import com.google.inject.Guice
import com.google.inject.Module
import misk.inject.uninject
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.reflect.full.createInstance

internal class MiskTestExtension: BeforeTestExecutionCallback, AfterTestExecutionCallback {
    override fun beforeTestExecution(context: ExtensionContext?) {
        val annotation = extractMiskTestAnn(context!!)
        val modules = createModulesFromAnnotation(annotation)

        val injector = Guice.createInjector(modules)
        injector.injectMembers(context.requiredTestInstance)
    }

    override fun afterTestExecution(context: ExtensionContext?) {
        uninject(context!!.requiredTestInstance)
    }

    private fun extractMiskTestAnn(context: ExtensionContext): MiskTest {
        // TODO: add support for annotated superclasses and interfaces
        return context.requiredTestClass.getAnnotation(MiskTest::class.java)
    }

    private fun createModulesFromAnnotation(annotation: MiskTest): Set<Module> {
        val instantiatedModules = annotation.modules.map { k -> k.createInstance() }
        val providedModules = annotation.moduleProvider.createInstance().modules()
        return providedModules.union(instantiatedModules)
    }
}

