package misk.testing

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Module
import com.google.inject.util.Modules
import misk.inject.getInstance
import misk.inject.uninject
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal class MiskTestExtension : BeforeEachCallback, AfterEachCallback {
  override fun beforeEach(context: ExtensionContext) {
    val testModules = context.getActionTestModules()
        .asSequence()
        .toList()
        .toTypedArray()
    val callbackModules = if (context.startService()) serviceManagementModules else arrayOf()
    val modules = Modules.combine(MiskTestingModule(), *callbackModules, *testModules)

    val injector = Guice.createInjector(modules)
    injector.injectMembers(context.requiredTestInstance)
    context.store("injector", injector)

    injector.getInstance<Callbacks>()
        .beforeEach(context)
  }

  override fun afterEach(context: ExtensionContext) {
    val injector = context.retrieve<Injector>("injector")

    injector.getInstance<Callbacks>()
        .afterEach(context)
    uninject(context.requiredTestInstance)
  }

  class StartServicesBeforeEach : BeforeEachCallback {
    @Inject
    lateinit var serviceManager: ServiceManager

    override fun beforeEach(context: ExtensionContext) {
      if (context.startService()) {
        serviceManager.startAsync()
        serviceManager.awaitHealthy(5, TimeUnit.SECONDS)
      }
    }
  }

  class StopServicesAfterEach : AfterEachCallback {
    @Inject
    lateinit var serviceManager: ServiceManager

    override fun afterEach(context: ExtensionContext) {
      if (context.startService()) {
        serviceManager.stopAsync()
      }
    }
  }

  private val serviceManagementModules = arrayOf(
      BeforeEachExtensionModule.create<StartServicesBeforeEach>(),
      AfterEachExtensionModule.create<StopServicesAfterEach>()
  )

  class Callbacks : BeforeEachCallback, AfterEachCallback {
    @Inject
    @JvmSuppressWildcards
    lateinit var beforeEachCallbacks: Set<BeforeEachCallback>

    @Inject
    @JvmSuppressWildcards
    lateinit var afterEachCallbacks: Set<AfterEachCallback>

    override fun afterEach(context: ExtensionContext) {
      afterEachCallbacks.forEach { it.afterEach(context) }
    }

    override fun beforeEach(context: ExtensionContext) {
      beforeEachCallbacks.forEach { it.beforeEach(context) }
    }
  }
}

private fun ExtensionContext.startService(): Boolean {
  val namespace = ExtensionContext.Namespace.create(requiredTestClass)
  // First check the context cache
  return getStore(namespace).getOrComputeIfAbsent(
      "startService",
      { requiredTestClass.getAnnotationsByType(MiskTest::class.java)[0].startService },
      Boolean::class.java
  )
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
      .filter { it.isAnnotationPresent(MiskTestModule::class.java) }
      .map {
        it.isAccessible = true
        it.get(requiredTestInstance) as Module
      }
}

