package misk.web.actions

import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.util.Modules
import misk.MiskModule
import misk.healthchecks.FakeHealthCheck
import misk.healthchecks.FakeHealthCheckModule
import misk.healthchecks.HealthStatus
import misk.services.FakeServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class StatusActionTest {
  @MiskTestModule
  val module = Modules.combine(
      MiskModule(),
      FakeServiceModule(),
      FakeHealthCheckModule()
  )

  @Inject lateinit var statusAction: StatusAction
  @Inject lateinit var serviceManager: ServiceManager
  @Inject lateinit var healthCheck: FakeHealthCheck

  @Test
  fun readinessDependsOnServiceStateAndHealthChecksPassing() {
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    assertThat(statusAction.getStatus()).isEqualTo(
        StatusAction.ServerStatus(
            mapOf("FakeService" to Service.State.RUNNING),
            mapOf("FakeHealthCheck" to HealthStatus(isHealthy = true, messages = listOf()))))

    healthCheck.setUnhealthy("things are failing", "this is not good")
    assertThat(statusAction.getStatus()).isEqualTo(
        StatusAction.ServerStatus(
            mapOf("FakeService" to Service.State.RUNNING),
            mapOf("FakeHealthCheck" to HealthStatus(
                isHealthy = false,
                messages = listOf("things are failing", "this is not good")))))

    healthCheck.setHealthy("everything is fine now")
    assertThat(statusAction.getStatus()).isEqualTo(
        StatusAction.ServerStatus(
            mapOf("FakeService" to Service.State.RUNNING),
            mapOf("FakeHealthCheck" to HealthStatus(
                isHealthy = true,
                messages = listOf("everything is fine now")))))

    serviceManager.stopAsync()
    serviceManager.awaitStopped()
    assertThat(statusAction.getStatus()).isEqualTo(
        StatusAction.ServerStatus(
            mapOf("FakeService" to Service.State.TERMINATED),
            mapOf("FakeHealthCheck" to HealthStatus(
                isHealthy = true,
                messages = listOf("everything is fine now")))))
  }
}
