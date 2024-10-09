package misk.web.actions

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.util.Modules
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.ServiceModule
import misk.healthchecks.FakeHealthCheckModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.logging.getLogger

@MiskTest
class ReadinessCheckServiceTest {
  @MiskTestModule
  val module = Modules.combine(
    TestWebActionModule(),
    FakeHealthCheckModule(),
    ServiceModule<ReadinessTestService>()
  )

  @Inject lateinit var clock: FakeClock
  @Inject internal lateinit var readinessCheckService: ReadinessCheckService
  @Inject lateinit var serviceManager: ServiceManager

  @Test fun waitsForRunningServicesBeforeHealthChecks() {
    assertThat(ReadinessTestService.instanceCreated).isFalse()
    assertThat(readinessCheckService.status).isNull()

    readinessCheckService.refreshStatuses()

    // Ensure that refreshStatuses does not result in the service instance being injected.
    // This should only happen as part of the ServiceManager startup.
    assertThat(ReadinessTestService.instanceCreated).isFalse()

    // still null
    assertThat(readinessCheckService.status).isNull()

    serviceManager.startAsync()
    serviceManager.awaitHealthy()

    assertThat(ReadinessTestService.instanceCreated).isTrue()

    // null until refreshed
    assertThat(readinessCheckService.status).isNull()

    readinessCheckService.refreshStatuses()
    assertThat(readinessCheckService.status?.lastUpdate).isEqualTo(clock.instant())

    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    // status cleared
    assertThat(readinessCheckService.status).isNull()
  }

  @Singleton
  internal class ReadinessTestService @Inject constructor() : AbstractIdleService() {
    init {
      logger.info { "Created" }
      instanceCreated = true
    }

    override fun startUp() {
      logger.info { "Started" }
    }

    override fun shutDown() {
      logger.info { "Shutdown" }
    }

    companion object {
      private val logger = getLogger<ReadinessTestService>()
      var instanceCreated = false
    }
  }
}
