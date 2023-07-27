package misk.web.actions

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.util.Modules
import misk.healthchecks.FakeHealthCheck
import misk.healthchecks.FakeHealthCheckModule
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

@MiskTest
class ReadinessCheckActionTest {
  @MiskTestModule
  val module = Modules.combine(
    TestWebActionModule(),
    FakeServiceModule(),
    FakeHealthCheckModule(),
    WebActionModule.create<ReadinessCheckAction>(),
  )

  @Inject lateinit var clock: FakeClock
  @Inject lateinit var config: WebConfig
  @Inject lateinit var readinessCheckAction: ReadinessCheckAction
  @Inject internal lateinit var readinessCheckService: ReadinessCheckService
  @Inject lateinit var serviceManager: ServiceManager
  @Inject lateinit var healthCheck: FakeHealthCheck

  @Test fun readinessDependsOnServiceStateAndHealthChecksPassing() {
    // Starts unhealthy
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(503)

    serviceManager.startAsync()
    serviceManager.awaitHealthy()

    // Healthy after start
    updateStatus()
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(200)

    updateStatus { healthCheck.setUnhealthy() }
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(503)

    updateStatus { healthCheck.setHealthy() }
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(200)

    // unhealthy when stale
    clock.add((config.readiness_max_age_ms + 1).toLong(), TimeUnit.MILLISECONDS)
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(503)

    serviceManager.stopAsync()
    serviceManager.awaitStopped()
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(503)
  }

  private fun updateStatus(fn: () -> Unit = {}) {
    fn()
    readinessCheckService.refreshStatuses()
  }
}
