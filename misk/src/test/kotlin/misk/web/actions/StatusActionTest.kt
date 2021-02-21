package misk.web.actions

import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.healthchecks.FakeHealthCheck
import misk.healthchecks.FakeHealthCheckModule
import misk.healthchecks.HealthStatus
import misk.services.FakeServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebActionModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class StatusActionTest {
  @MiskTestModule
  val module = Modules.combine(
    MiskTestingServiceModule(),
    FakeServiceModule(),
    FakeHealthCheckModule(),
    WebActionModule.create<StatusAction>()
  )

  @Inject lateinit var statusAction: StatusAction
  @Inject lateinit var serviceManager: ServiceManager
  @Inject lateinit var healthCheck: FakeHealthCheck

  @Test
  fun readinessDependsOnServiceStateAndHealthChecksPassing() {
    serviceManager.startAsync()
    serviceManager.awaitHealthy()

    var status = statusAction.getStatus()
    assertThat(status.serviceStatus).containsEntry("FakeService", Service.State.RUNNING)
    assertThat(status.healthCheckStatus).containsEntry(
      "FakeHealthCheck", HealthStatus(isHealthy = true, messages = listOf())
    )

    healthCheck.setUnhealthy("things are failing", "this is not good")
    status = statusAction.getStatus()
    assertThat(status.serviceStatus).containsEntry("FakeService", Service.State.RUNNING)
    assertThat(status.healthCheckStatus).containsEntry(
      "FakeHealthCheck",
      HealthStatus(
        isHealthy = false,
        messages = listOf("things are failing", "this is not good")
      )
    )

    healthCheck.setHealthy("everything is fine now")
    status = statusAction.getStatus()
    assertThat(status.serviceStatus).containsEntry("FakeService", Service.State.RUNNING)
    assertThat(status.healthCheckStatus).containsEntry(
      "FakeHealthCheck",
      HealthStatus(
        isHealthy = true,
        messages = listOf("everything is fine now")
      )
    )

    serviceManager.stopAsync()
    serviceManager.awaitStopped()
    status = statusAction.getStatus()
    assertThat(status.serviceStatus).containsEntry("FakeService", Service.State.TERMINATED)
    assertThat(status.healthCheckStatus).containsEntry(
      "FakeHealthCheck",
      HealthStatus(
        isHealthy = true,
        messages = listOf("everything is fine now")
      )
    )
  }
}
