package misk.web.actions

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.healthchecks.FakeHealthCheck
import misk.healthchecks.FakeHealthCheckModule
import misk.services.FakeServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebActionModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class ReadinessCheckActionTest {
  @MiskTestModule
  val module = Modules.combine(
    MiskTestingServiceModule(),
    FakeServiceModule(),
    FakeHealthCheckModule(),
    WebActionModule.create<ReadinessCheckAction>()
  )

  @Inject lateinit var readinessCheckAction: ReadinessCheckAction
  @Inject lateinit var serviceManager: ServiceManager
  @Inject lateinit var healthCheck: FakeHealthCheck

  @Test fun readinessDependsOnServiceStateAndHealthChecksPassing() {
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(200)

    healthCheck.setUnhealthy()
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(503)
    healthCheck.setHealthy()
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(200)

    serviceManager.stopAsync()
    serviceManager.awaitStopped()
    assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(503)
  }
}
