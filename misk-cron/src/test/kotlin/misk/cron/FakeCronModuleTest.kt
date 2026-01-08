package misk.cron

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.lang.Thread.sleep
import java.time.ZoneId
import misk.MiskTestingServiceModule
import misk.ReadyService
import misk.ServiceModule
import misk.clustering.fake.lease.FakeLeaseModule
import misk.clustering.weights.FakeClusterWeightModule
import misk.inject.KAbstractModule
import misk.inject.toKey
import misk.logging.LogCollector
import misk.logging.LogCollectorModule
import misk.logging.getLogger
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class FakeCronModuleTest {
  @Suppress("unused")
  @MiskTestModule
  val module =
    object : KAbstractModule() {
      override fun configure() {
        install(FakeLeaseModule())
        install(MiskTestingServiceModule())
        install(LogCollectorModule())

        install(ServiceModule<DependentService>().enhancedBy<ReadyService>())
        install(FakeClusterWeightModule())
        install(FakeCronModule(ZoneId.of("America/Toronto"), dependencies = listOf(DependentService::class.toKey())))
        install(CronEntryModule.create<MinuteCron>())
      }
    }

  @Inject private lateinit var logCollector: LogCollector

  @Test
  fun dependentServicesStartUpBeforeCron() {
    assertThat(logCollector.takeMessages())
      .containsExactly(
        "DependentService started",
        "Starting ready service",
        "CronService started",
        "Adding cron entry misk.cron.MinuteCron, crontab=* * * * *",
      )
  }

  @Singleton
  private class DependentService @Inject constructor() : AbstractIdleService() {
    override fun startUp() {
      sleep(1000)
      logger.info { "DependentService started" }
    }

    override fun shutDown() {}

    companion object {
      val logger = getLogger<DependentService>()
    }
  }
}
