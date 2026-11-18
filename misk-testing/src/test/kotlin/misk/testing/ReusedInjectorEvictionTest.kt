package misk.testing

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import com.google.inject.Injector
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.lang.ref.WeakReference
import misk.ServiceManagerModule
import misk.ServiceModule
import misk.inject.ReusableTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener

/** Runs from the `injectorReuseLruTest` task, which turns on injector reuse with an LRU size of 1. */
class ReusedInjectorEvictionTest {
  @Test
  fun `an evicted injector can be garbage collected`() {
    assumeReuseWithLruSizeOfOne()
    runFixtures(FirstFixture::class.java, SecondFixture::class.java)

    val evicted = firstInjector!!
    for (attempt in 1..50) {
      System.gc()
      if (evicted.get() == null) break
      Thread.sleep(100)
    }
    assertThat(evicted.get()).isNull()
  }

  @Test
  fun `the exit hook stops the services of cached injectors`() {
    assumeReuseWithLruSizeOfOne()
    runFixtures(FirstFixture::class.java)
    val service = firstInjector!!.get()!!.getInstance(IdleService::class.java)
    assertThat(service.isRunning).isTrue()

    MiskTestExtension.stopRunningReusedInjectors()

    assertThat(service.state()).isEqualTo(Service.State.TERMINATED)
  }

  private fun assumeReuseWithLruSizeOfOne() {
    assumeTrue(System.getenv("MISK_TEST_REUSE_INJECTOR") == "true")
    assumeTrue(System.getenv("MISK_TEST_REUSE_LRU_SIZE") == "1")
  }

  private fun runFixtures(vararg fixtures: Class<*>) {
    System.setProperty(FIXTURE_PROPERTY, "true")
    try {
      fixtures.forEach { runFixture(it) }
    } finally {
      System.clearProperty(FIXTURE_PROPERTY)
    }
  }

  private fun runFixture(fixture: Class<*>) {
    val listener = SummaryGeneratingListener()
    LauncherFactory.create()
      .execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass(fixture)).build(), listener)
    assertThat(listener.summary.testsSucceededCount).isEqualTo(1)
  }

  @MiskTest(startService = true)
  @EnabledIfSystemProperty(named = FIXTURE_PROPERTY, matches = "true")
  class FirstFixture {
    @MiskTestModule val module = FirstModule()

    @Inject lateinit var injector: Injector

    @Test
    fun `records the injector`() {
      firstInjector = WeakReference(injector)
    }
  }

  @MiskTest(startService = true)
  @EnabledIfSystemProperty(named = FIXTURE_PROPERTY, matches = "true")
  class SecondFixture {
    @MiskTestModule val module = SecondModule()

    @Test fun `evicts the first injector`() {}
  }

  class FirstModule : ReusableTestModule() {
    override fun configure() {
      install(ServiceManagerModule())
      install(ServiceModule<IdleService>())
    }
  }

  class SecondModule : ReusableTestModule() {
    override fun configure() {
      install(ServiceManagerModule())
      install(ServiceModule<IdleService>())
    }
  }

  @Singleton
  class IdleService @Inject constructor() : AbstractIdleService() {
    override fun startUp() {}

    override fun shutDown() {}
  }

  companion object {
    private const val FIXTURE_PROPERTY = "misk.testing.reusedInjectorEvictionFixture"
    private var firstInjector: WeakReference<Injector>? = null
  }
}
