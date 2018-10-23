package misk.testing

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Module
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.inject.uninject
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal class MiskTestExtension : BeforeEachCallback, AfterEachCallback {
  override fun beforeEach(context: ExtensionContext) {
    val module = object : KAbstractModule() {
      override fun configure() {
        if (context.startService()) {
          multibind<BeforeEachCallback>().to<StartServicesBeforeEach>()
          multibind<AfterEachCallback>().to<StopServicesAfterEach>()
        }
        for (module in context.getActionTestModules()) {
          install(module)
        }
        multibind<BeforeEachCallback>().to<InjectUninject>()
        multibind<AfterEachCallback>().to<InjectUninject>()

        // Initialize empty sets for our multibindings.
        newMultibinder<BeforeEachCallback>()
        newMultibinder<AfterEachCallback>()
      }
    }

    val injector = Guice.createInjector(module)
    context.store("injector", injector)

    injector.getInstance<Callbacks>().beforeEach(context)
  }

  override fun afterEach(context: ExtensionContext) {
    val injector = context.retrieve<Injector>("injector")

    injector.getInstance<Callbacks>().afterEach(context)
    uninject(context.requiredTestInstance)
  }

  class StartServicesBeforeEach : BeforeEachCallback {
    @Inject
    lateinit var serviceManager: ServiceManager

    override fun beforeEach(context: ExtensionContext) {
      if (context.startService()) {
        serviceManager.startAsync().awaitHealthy(600, TimeUnit.SECONDS)
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
      serviceManager.awaitStopped(20, TimeUnit.SECONDS)
    }
  }

  /** We inject after starting services and uninject after stopping services. */
  @Singleton
  class InjectUninject : BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
      val injector = context.retrieve<Injector>("injector")
      injector.injectMembers(context.requiredTestInstance)
    }

    override fun afterEach(context: ExtensionContext) {
      uninject(context.requiredTestInstance)
    }
  }

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
  return getStore(namespace).getOrComputeIfAbsent("startService",
      { requiredTestClass.getAnnotationsByType(MiskTest::class.java)[0].startService },
      Boolean::class.java)
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

