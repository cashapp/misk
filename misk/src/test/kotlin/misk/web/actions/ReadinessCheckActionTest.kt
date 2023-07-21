package misk.web.actions

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.healthchecks.FakeHealthCheck
import misk.healthchecks.FakeHealthCheckModule
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import misk.inject.KAbstractModule
import misk.services.FakeServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import misk.web.WebActionModule
import misk.web.WebConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest
class ReadinessCheckActionTest {
  @MiskTestModule
  val module = Modules.combine(
    MiskTestingServiceModule(),
    FakeServiceModule(),
    FakeHealthCheckModule(),
    WebActionModule.create<ReadinessCheckAction>(),
    object : KAbstractModule() {
      override fun configure() {
        multibind<HealthCheck>().to<CountingHealthCheck>()
        bind<WebConfig>().toInstance(WebConfig(8000))
      }
    }
  )

  @Inject lateinit var clock: FakeClock
  @Inject lateinit var readinessCheckAction: ReadinessCheckAction
  @Inject lateinit var serviceManager: ServiceManager
  @Inject lateinit var fakeHealthCheck: FakeHealthCheck
  @Inject lateinit var countingHealthCheck: CountingHealthCheck

  @Test fun readinessDependsOnServiceStateAndHealthChecksPassing() {
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(200)

    fakeHealthCheck.setUnhealthy()
    forceRefresh()
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(503)

    fakeHealthCheck.setHealthy()
    forceRefresh()
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(200)

    serviceManager.stopAsync()
    serviceManager.awaitStopped()
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(503)
  }

  @Test fun readinessReusesStatusUntilElapsedTtl() {
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(200)
    assertThat(countingHealthCheck.checks).isEqualTo(1)

    // still the same
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(200)
    assertThat(countingHealthCheck.checks).isEqualTo(1)

    // move up and verify count increases just once
    forceRefresh()
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(200)
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(200)
    assertThat(countingHealthCheck.checks).isEqualTo(2)
  }

  @Test fun readinessReturnsErrorPastMaxAgeThenRefreshes() {
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(200)

    // move past max age
    clock.add((readinessCheckAction.config.readiness_max_age_ms + 1).toLong(), TimeUnit.MILLISECONDS)
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(503)

    // Next run should still see the updated state
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(200)
  }

  private fun forceRefresh() {
    // advance time so that a refresh is triggered
    clock.add((readinessCheckAction.config.readiness_refresh_interval_ms + 1).toLong(), TimeUnit.MILLISECONDS)
    // first hit will still return cache and queue update in background
    // result can be ignored since it will be the same as previous
    readinessCheckAction.readinessCheck()
  }

  @Singleton
  class CountingHealthCheck @Inject constructor() : HealthCheck {
    var checks = 0
    var status = HealthStatus.healthy()

    override fun status(): HealthStatus{
      checks++
      return status
    }
  }
}
