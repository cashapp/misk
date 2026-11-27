package misk.testing

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.ServiceManagerModule
import misk.ServiceModule
import misk.inject.KAbstractModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener

class ServiceStartupFailureTest {
  @Test
  fun `startup failure is reported before entering the test body`() {
    val listener = SummaryGeneratingListener()
    System.setProperty(FIXTURE_PROPERTY, "true")
    try {
      val request = LauncherDiscoveryRequestBuilder.request().selectors(selectClass(FailingFixture::class.java)).build()
      LauncherFactory.create().execute(request, listener)
    } finally {
      System.clearProperty(FIXTURE_PROPERTY)
    }

    val failure = listener.summary.failures.single().exception
    assertThat(failure).hasStackTraceContaining("fixture service could not start")
  }

  @MiskTest(startService = true)
  @EnabledIfSystemProperty(named = FIXTURE_PROPERTY, matches = "true")
  class FailingFixture {
    @MiskTestModule
    val module =
      object : KAbstractModule() {
        override fun configure() {
          install(ServiceManagerModule())
          install(ServiceModule<FailingService>())
        }
      }

    @Test
    fun `test body does not run`() {
      error("entered the test body")
    }
  }

  @Singleton
  class FailingService @Inject constructor() : AbstractIdleService() {
    override fun startUp() = error("fixture service could not start")

    override fun shutDown() {}
  }

  companion object {
    private const val FIXTURE_PROPERTY = "misk.testing.startupFailureFixture"
  }
}
