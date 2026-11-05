package misk.testing

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.cache.RemovalCause
import com.google.common.util.concurrent.ServiceManager
import com.google.common.util.concurrent.UncheckedExecutionException
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.Module
import com.google.inject.testing.fieldbinder.Bind
import com.google.inject.testing.fieldbinder.BoundFieldModule
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import misk.inject.KAbstractModule
import misk.inject.ReusableTestModule
import misk.inject.getInstance
import misk.inject.uninject
import misk.logging.getLogger
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

internal class MiskTestExtension : BeforeEachCallback, AfterEachCallback {

  companion object {
    private val runningDependencies = ConcurrentHashMap.newKeySet<String>()
    private val runningServices = ConcurrentHashMap.newKeySet<List<Module>>()
    private val log = getLogger<MiskTestExtension>()

    private val maxLruSize: Int? = System.getenv("MISK_TEST_REUSE_LRU_SIZE")?.toIntOrNull()?.takeIf { it > 0 }

    // When MISK_TEST_REUSE_LRU_SIZE is set, the cache evicts least-recently-used entries once
    // the size is exceeded; the removal listener stops services for evicted injectors. Guava's
    // cache uses ConcurrentMap internally, so callers don't need extra synchronization.
    private val injectedModules: Cache<List<Module>, CachedInjector> = run {
      val builder =
        CacheBuilder.newBuilder().removalListener<List<Module>, CachedInjector> { notification ->
          if (notification.cause == RemovalCause.EXPLICIT) return@removalListener
          val key = notification.key ?: return@removalListener
          val injector = notification.value?.injector ?: return@removalListener
          runningServices.remove(key)
          try {
            val serviceManager = injector.getExistingBinding(Key.get(ServiceManager::class.java))?.provider?.get()
            serviceManager?.stopAsync()?.awaitStopped(45, TimeUnit.SECONDS)
          } catch (e: Exception) {
            log.warn(e) { "Failed to stop services for evicted injector cache entry" }
          }
        }
      if (maxLruSize != null) builder.maximumSize(maxLruSize.toLong())
      builder.build()
    }

    /**
     * A reused injector, along with the test class whose [Bind] fields were baked into it when it was created.
     * [ReusableTestModule] equality is structural, so distinct test classes with equal module lists share a cache
     * entry; only the owner's [Bind] fields are bound.
     */
    private class CachedInjector(val injector: Injector, val ownerTestClass: Class<*>)
  }

  override fun beforeEach(context: ExtensionContext) {
    for (dep in context.getExternalDependencies()) {
      dep.startIfNecessary()
    }

    for (dep in context.getExternalDependencies()) {
      dep.beforeEach()
    }

    val module =
      object : KAbstractModule() {
        override fun configure() {
          binder().requireAtInjectOnConstructors()

          if (context.startService() && !context.reuseInjector()) {
            multibind<AfterEachCallback>().to<StopServicesAfterEach>()
          }

          for (module in context.getActionTestModules()) {
            install(module)
          }

          context.requiredTestInstances.allInstances.forEach { install(BoundFieldModule.of(it)) }

          multibind<BeforeEachCallback>().to<InjectUninject>()
          multibind<AfterEachCallback>().to<InjectUninject>()

          // Initialize empty sets for our multibindings.
          newMultibinder<BeforeEachCallback>()
          newMultibinder<AfterEachCallback>()
          newMultibinder<TestFixture>()
        }
      }

    val injector =
      if (context.reuseInjector()) {
        val key = context.getSortedActionTestModules()
        // ReusableTestModule equality is structural, so different test classes can share a cache
        // entry. A cached injector only has the @Bind fields of the test class that created it;
        // a different class reusing it would have its own @Bind fields silently ignored. Fail
        // fast with an actionable error instead of letting Guice produce confusing
        // MissingImplementation errors (or worse, silently resolve the wrong bindings).
        injectedModules.getIfPresent(key)?.let { cached ->
          if (cached.ownerTestClass != context.rootRequiredTestClass) {
            check(!context.declaresBindFields()) {
              """
              |${context.rootRequiredTestClass.name} declares @Bind fields but is reusing the injector cached
              |for ${cached.ownerTestClass.name}: both test classes' @MiskTestModule modules compare equal,
              |so the cached injector was built with ${cached.ownerTestClass.name}'s @Bind fields and this
              |class's would be silently ignored.
              |
              |Fix it one of these ways:
              |  - Give this test a module that only compares equal to itself, e.g. install the shared
              |    module(s) from a ReusableTestModule subclass declared next to the test class.
              |  - Remove the @Bind fields and bind the test doubles in the module instead.
              |  - Opt this test module out of injector reuse (miskTestReuseInjector = false, or unset
              |    MISK_TEST_REUSE_INJECTOR).
              """
                .trimMargin()
            }
          }
        }
        try {
            injectedModules.get(key) { CachedInjector(Guice.createInjector(module), context.rootRequiredTestClass) }
          } catch (e: UncheckedExecutionException) {
            throw e.cause ?: e
          }
          .injector
      } else {
        Guice.createInjector(module)
      }
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

  class StartServicesBeforeEach @Inject constructor() {
    @com.google.inject.Inject(optional = true) var serviceManager: ServiceManager? = null

    fun beforeEach(context: ExtensionContext) {
      if (context.startService()) {
        if (serviceManager == null) {
          throw IllegalStateException(
            "This test is configured with `startService` set to true, " +
              "but no ServiceManager is bound. Did you forget to install MiskTestingServiceModule?"
          )
        }
        if (context.reuseInjector() && runningServices.contains(context.getSortedActionTestModules())) {
          return
        }
        try {
          try {
            serviceManager!!.startAsync().awaitHealthy(60, TimeUnit.SECONDS)
          } catch (e: Exception) {
            if (context.reuseInjector()) {
              // The `ServiceManager` requires services to be in a NEW state when starting them,
              // so if services fail to start, we need to stop them and remove the injector from the cache,
              // so that the next test can start fresh.
              try {
                serviceManager!!.stop(context)
              } catch (stopError: Exception) {
                e.addSuppressed(stopError)
              }
              injectedModules.invalidate(context.getSortedActionTestModules())
              throw e
            }
          }
          runningServices.add(context.getSortedActionTestModules())
          if (context.reuseInjector()) {
            Runtime.getRuntime().addShutdownHook(thread(start = false) { serviceManager!!.stop(context) })
          }
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
    @Inject lateinit var serviceManager: ServiceManager

    override fun afterEach(context: ExtensionContext) {
      if (context.startService()) {
        serviceManager.stop(context)
      }
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

  class Callbacks
  @Inject
  constructor(
    private val startServicesBeforeEach: StartServicesBeforeEach,
    private val beforeEachCallbacks: Set<BeforeEachCallback>,
    private val afterEachCallbacks: Set<AfterEachCallback>,
    private val testFixtures: Set<TestFixture>,
  ) : BeforeEachCallback, AfterEachCallback {

    override fun afterEach(context: ExtensionContext) {
      afterEachCallbacks.forEach { it.afterEach(context) }
    }

    override fun beforeEach(context: ExtensionContext) {
      // Starting services first given some fixtures rely on services being started. For example,
      // the dynamo DB fixture needs the service to be started, in order to be able to delete data.
      startServicesBeforeEach.beforeEach(context)
      if (context.reuseInjector()) {
        testFixtures.forEach { it.reset() }
      }
      // Call the beforeEach callbacks after resetting fixtures, so that things like seeding test
      // data can be done in these callback and not be reset.
      beforeEachCallbacks.forEach { it.beforeEach(context) }
    }
  }

  private fun ExternalDependency.startIfNecessary() {
    if (!runningDependencies.contains(id)) {
      log.info { "starting $id" }
      startup()
      Runtime.getRuntime()
        .addShutdownHook(
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

// The injector is reused across tests if
//   1. The tests module(s) used in the test extend ReusableTestModules, AND
//   2. The environment variable MISK_TEST_REUSE_INJECTOR is set to true (the system property of
//      the same name is honored as a fallback so tests can exercise reuse in-process)
private fun ExtensionContext.reuseInjector(): Boolean {
  return getFromStoreOrCompute("reuseInjector") {
    val reuseInjectorEnabled =
      (System.getenv("MISK_TEST_REUSE_INJECTOR") ?: System.getProperty("MISK_TEST_REUSE_INJECTOR"))?.toBoolean()
        ?: false
    reuseInjectorEnabled && getActionTestModules().all { it is ReusableTestModule }
  }
}

private fun ExtensionContext.getActionTestModules(): Iterable<Module> {
  return getFromStoreOrCompute("module") { fieldsAnnotatedBy<MiskTestModule, Module>() }
}

/** Returns true if any of the current test instances (or their superclasses) declare a [Bind] field. */
private fun ExtensionContext.declaresBindFields(): Boolean =
  requiredTestInstances.allInstances.any { instance ->
    generateSequence(instance.javaClass) { it.superclass }
      .flatMap { it.declaredFields.asSequence() }
      .any { it.isAnnotationPresent(Bind::class.java) }
  }

private fun ExtensionContext.getSortedActionTestModules(): List<Module> {
  return getActionTestModules().sortedBy { it.javaClass.name }
}

private fun ExtensionContext.getExternalDependencies(): Iterable<ExternalDependency> {
  return getFromStoreOrCompute("external-dependencies") {
    fieldsAnnotatedBy<MiskExternalDependency, ExternalDependency>()
  }
}

private fun ServiceManager.stop(context: ExtensionContext) {
  this.stopAsync().awaitStopped(45, TimeUnit.SECONDS)
}
