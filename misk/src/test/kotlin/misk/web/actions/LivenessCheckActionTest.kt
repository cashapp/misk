package misk.web.actions

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.util.Modules
import misk.MiskModule
import misk.services.FakeServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class LivenessCheckActionTest {
  @MiskTestModule
  val module = Modules.combine(
      MiskModule(),
      FakeServiceModule()
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
    assertThat(livenessCheckAction.livenessCheck().statusCode).isEqualTo(503)
  }
}
