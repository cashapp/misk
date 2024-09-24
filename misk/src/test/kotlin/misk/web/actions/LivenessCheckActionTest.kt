package misk.web.actions

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.util.Modules
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.services.FakeServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebActionModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest
class LivenessCheckActionTest {
  @MiskTestModule
  val module = Modules.combine(
    MiskTestingServiceModule(),
    FakeServiceModule(),
    WebActionModule.create<LivenessCheckAction>()
  )

  @Inject lateinit var livenessCheckAction: LivenessCheckAction
  @Inject lateinit var serviceManager: ServiceManager

  @Test
  fun livenessDependsOnServiceState() {
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    assertThat(livenessCheckAction.livenessCheck().statusCode).isEqualTo(200)
    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    // liveness should not fail for terminated services (only failed)
    assertThat(livenessCheckAction.livenessCheck().statusCode).isEqualTo(200)
  }
}
