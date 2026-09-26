package misk.testing

import com.google.common.util.concurrent.AbstractService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.MiskTestingServiceModule
import misk.ServiceModule
import misk.inject.KAbstractModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request
import org.junit.platform.launcher.core.LauncherFactory.create
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.launcher.listeners.TestExecutionSummary


/**
 * Borrows from https://stackoverflow.com/questions/47237611/check-that-junit-extension-throws-specific-exception
 */
class ServiceStartupThrowsTest {

  @Test
  fun `tests should fail if a service fails to start`() {
    val listener = SummaryGeneratingListener()
    val request: LauncherDiscoveryRequest? = request().selectors(
      DiscoverySelectors.selectMethod(
        FailingServiceStartupTest::class.java,
        "service should have failed to start"
      )
    ).build()
    create().execute(request, listener)
    val summary: TestExecutionSummary = listener.getSummary()

    assertThat(summary.getTestsFailedCount()).isEqualTo(1)
    assertThat(summary.getFailures().get(0).getException())
      .isInstanceOf(RuntimeException::class.java)
      .hasMessage("Just five more minutes Mom!")
  }

  // This should not run as it's not marked as @Internal
  @MiskTest(startService = true)
  internal class FailingServiceStartupTest {
    @MiskTestModule
    val module =
      object : KAbstractModule() {
        override fun configure() {
          install(MiskTestingServiceModule())
          install(ServiceModule<FailingService>())
        }
      }
    @Test
    fun `service should have failed to start`(){
      fail("service should have thrown and failed the test")
    }
  }

  @Singleton
  class FailingService @Inject constructor(): AbstractService(){
    override fun doStart() {
      throw RuntimeException("Just five more minutes Mom!")
    }

    override fun doStop() {
      fail("should not be called")
    }

  }
}
