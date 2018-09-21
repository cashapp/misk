package misk.service

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import com.google.inject.Key
import misk.DependentService
import misk.MiskServiceModule
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.inject.keyOf
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

internal class ServiceTestingModuleTest {
  @Test fun serviceTestModuleAddsTestSpecificDependency() {
    val injector = Guice.createInjector(object : KAbstractModule() {
      override fun configure() {
        install(MiskServiceModule())
        multibind<Service>().to<LowestLevelService>()
        install(ServiceTestingModule.withExtraDependencies<AppService>(
            keyOf<ExternalServiceSpinupService>())
        )
        multibind<Service>().to<ExternalServiceSpinupService>()
        bind<FakeExternalClient>().to<ExternalServiceSpinupService>()
      }
    })

    val serviceManager = injector.getInstance<ServiceManager>()
    serviceManager.startAsync()
    serviceManager.awaitHealthy(2, TimeUnit.SECONDS)
    serviceManager.stopAsync()
    serviceManager.awaitStopped(2, TimeUnit.SECONDS)
  }

  /** A low level in process dependency that we always need */
  @Singleton
  class LowestLevelService : AbstractIdleService(), DependentService {
    override val consumedKeys: Set<Key<*>> = setOf()
    override val producedKeys: Set<Key<*>> = setOf(keyOf<LowestLevelService>())

    val started = AtomicBoolean(false)

    override fun startUp() {
      started.set(true)
    }

    override fun shutDown() {}
  }

  /**
   * Models an external client used by the app. Represents a dependency that is satisfied
   * externally outside of tests, but that requires additional infrastructure to spin up during
   * testing
   */
  interface FakeExternalClient {
    fun confirmRunning()
  }

  /** The application service, with a dependency on another app service and an external service */
  @Singleton
  class AppService @Inject internal constructor(
    private val lowestLevelService: LowestLevelService,
    private val client: FakeExternalClient
  ) : AbstractIdleService(), DependentService {

    override val consumedKeys: Set<Key<*>> = setOf(keyOf<LowestLevelService>())
    override val producedKeys: Set<Key<*>> = setOf()

    override fun startUp() {
      // Confirm the lowest level internal service was spun up first
      check(lowestLevelService.started.get()) { "LowestLevelService was not started" }

      // Confirm we can talk to the external service (in testing requires that we spin up
      // another service before this one)
      client.confirmRunning()
    }

    override fun shutDown() {}
  }

  /**
   * Models a dependency normally satisfied through a client to an external, but for testing
   * requires spinning up something in process
   */
  @Singleton
  class ExternalServiceSpinupService : AbstractIdleService(), DependentService,
      FakeExternalClient {
    override val consumedKeys: Set<Key<*>> = setOf()
    override val producedKeys: Set<Key<*>> = setOf(keyOf<ExternalServiceSpinupService>())

    private val started = AtomicBoolean(false)

    override fun confirmRunning() {
      check(started.get()) { "ExternalService was not started" }
    }

    override fun startUp() {
      started.set(true)
    }

    override fun shutDown() {}
  }
}