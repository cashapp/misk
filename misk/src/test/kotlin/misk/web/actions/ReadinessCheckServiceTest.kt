package misk.web.actions

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.util.Modules
import misk.healthchecks.FakeHealthCheckModule
import misk.services.FakeServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class ReadinessCheckServiceTest {
  @MiskTestModule
  val module = Modules.combine(
    TestWebActionModule(),
    FakeServiceModule(),
    FakeHealthCheckModule(),
  )

  @Inject lateinit var clock: FakeClock
  @Inject lateinit var readinessCheckService: ReadinessCheckService
  @Inject lateinit var serviceManager: ServiceManager

  @Test fun waitsForRunningServicesBeforeHealthChecks() {
    assertThat(readinessCheckService.status).isNull()

    readinessCheckService.refreshStatuses()

    // still null
    assertThat(readinessCheckService.status).isNull()

    serviceManager.startAsync()
    serviceManager.awaitHealthy()

    // null until refreshed
    assertThat(readinessCheckService.status).isNull()

    readinessCheckService.refreshStatuses()
    assertThat(readinessCheckService.status?.lastUpdate).isEqualTo(clock.instant())

    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    // status cleared
    assertThat(readinessCheckService.status).isNull()
  }
}
