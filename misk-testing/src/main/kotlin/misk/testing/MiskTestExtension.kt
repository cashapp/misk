package misk.testing

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Module
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.inject.uninject
import misk.logging.getLogger
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
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
    Environment.setTesting()

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
      injector.injectMembers(context.requiredTestInstance)
    }

    override fun afterEach(context: ExtensionContext) {
      uninject(context.requiredTestInstance)
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
      Runtime.getRuntime().addShutdownHook(Thread {
        log.info { "stopping $id" }
        shutdown()
      })
      runningDependencies.add(id)
    } else {
      log.info { "$id already running, not starting anything" }
    }
  }
}

@Deprecated("Start Services always.")
private fun ExtensionContext.startService(): Boolean {
  return true
}

private fun ExtensionContext.getActionTestModules(): Iterable<Module> {
  val namespace = ExtensionContext.Namespace.create(requiredTestClass)
  // First check the context cache
  @Suppress("UNCHECKED_CAST")
  return getStore(namespace).getOrComputeIfAbsent("module",
      { modulesViaReflection() }) as Iterable<Module>
}

// Find [MiskTestModule]-annotated [Module]s on the test class and, recursively, its base classes.
private fun ExtensionContext.modulesViaReflection(): Iterable<Module> {
  return generateSequence(requiredTestClass) { c -> c.superclass }
      .flatMap { it.declaredFields.asSequence() }
      .filter { it.isAnnotationPresent(MiskTestModule::class.java) }
      .map {
        it.isAccessible = true
        it.get(requiredTestInstance) as Module
      }
      .toList()
}

private fun ExtensionContext.getExternalDependencies(): Iterable<ExternalDependency> {
  val namespace = ExtensionContext.Namespace.create(requiredTestClass)
  // First check the context cache
  @Suppress("UNCHECKED_CAST")
  return getStore(namespace).getOrComputeIfAbsent("external-dependencies") {
    externalDependenciesViaReflection()
  } as Iterable<ExternalDependency>
}

// Find [MiskExternalDependency]-annotated [ExternalDependency]s on the test class and, recursively,
// its base classes.
private fun ExtensionContext.externalDependenciesViaReflection(): Iterable<ExternalDependency> {
  return generateSequence(requiredTestClass) { c -> c.superclass }
      .flatMap { it.declaredFields.asSequence() }
      .filter { it.isAnnotationPresent(MiskExternalDependency::class.java) }
      .map {
        it.isAccessible = true
        it.get(requiredTestInstance) as ExternalDependency
      }
      .toList()
}
