package misk.testing

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Module
import com.google.inject.testing.fieldbinder.BoundFieldModule
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.inject.uninject
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import wisp.logging.getLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal class MiskTestExtension : BeforeEachCallback, AfterEachCallback {

  companion object {
    private val runningDependencies = ConcurrentHashMap.newKeySet<String>()
    private val log = getLogger<MiskTestExtension>()
  }

  override fun beforeEach(context: ExtensionContext) {
    for (dep in context.getExternalDependencies()) {
      dep.startIfNecessary()
    }

    for (dep in context.getExternalDependencies()) {
      dep.beforeEach()
    }

    val module = object : KAbstractModule() {
      override fun configure() {
        binder().requireAtInjectOnConstructors()

        if (context.startService()) {
          multibind<BeforeEachCallback>().to<StartServicesBeforeEach>()
          multibind<AfterEachCallback>().to<StopServicesAfterEach>()
        }
        for (module in context.getActionTestModules()) {
          install(module)
        }

        context.requiredTestInstances.allInstances.forEach { install(BoundFieldModule.of(it)) }

        multibind<BeforeEachCallback>().to<InjectUninject>()
        multibind<BeforeEachCallback>().to<LogLevelExtension>()
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
    uninject(context.rootRequiredTestInstance)

    for (dep in context.getExternalDependencies()) {
      dep.afterEach()
    }
  }

  class StartServicesBeforeEach @Inject constructor() : BeforeEachCallback {
    @Inject
    lateinit var serviceManager: ServiceManager

    override fun beforeEach(context: ExtensionContext) {
      if (context.startService()) {
        try {
          serviceManager.startAsync().awaitHealthy(60, TimeUnit.SECONDS)
        } catch (e: IllegalStateException) {
          // Unwrap and throw the real service failure
          val suppressed = e.suppressed.firstOrNull()
          val cause = suppressed?.cause
          if (cause != null) {
            throw cause
          }
          throw e
        }
      }
    }
  }

  class StopServicesAfterEach @Inject constructor() : AfterEachCallback {
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
  class InjectUninject @Inject constructor() : BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
      val injector = context.retrieve<Injector>("injector")
      context.requiredTestInstances.allInstances.forEach { injector.injectMembers(it) }
    }

    override fun afterEach(context: ExtensionContext) {
      context.requiredTestInstances.allInstances.forEach { uninject(it) }
    }
  }

  class Callbacks @Inject constructor(
    private val beforeEachCallbacks: Set<BeforeEachCallback>,
    private val afterEachCallbacks: Set<AfterEachCallback>
  ) : BeforeEachCallback, AfterEachCallback {

    override fun afterEach(context: ExtensionContext) {
      afterEachCallbacks.forEach { it.afterEach(context) }
    }

    override fun beforeEach(context: ExtensionContext) {
      beforeEachCallbacks.forEach { it.beforeEach(context) }
    }
  }

  private fun ExternalDependency.startIfNecessary() {
    if (!runningDependencies.contains(id)) {
      log.info { "starting $id" }
      startup()
      Runtime.getRuntime().addShutdownHook(
        Thread {
          log.info { "stopping $id" }
          shutdown()
        }
      )
      runningDependencies.add(id)
    } else {
      log.info { "$id already running, not starting anything" }
    }
  }
}

private fun ExtensionContext.startService(): Boolean {
  return getFromStoreOrCompute("startService") {
    rootRequiredTestClass.getAnnotationsByType(MiskTest::class.java)[0].startService
  }
}

private fun ExtensionContext.getActionTestModules(): Iterable<Module> {
  return getFromStoreOrCompute("module") { fieldsAnnotatedBy<MiskTestModule, Module>() }
}

private fun ExtensionContext.getExternalDependencies(): Iterable<ExternalDependency> {
  return getFromStoreOrCompute("external-dependencies") {
    fieldsAnnotatedBy<MiskExternalDependency, ExternalDependency>()
  }
}
