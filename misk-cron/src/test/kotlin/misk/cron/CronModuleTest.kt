package misk.cron

import com.google.common.util.concurrent.AbstractIdleService
import misk.MiskTestingServiceModule
import misk.ServiceModule
import misk.clustering.fake.lease.FakeLeaseModule
import misk.inject.KAbstractModule
import misk.inject.toKey
import misk.logging.LogCollectorModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.logging.LogCollector
import wisp.logging.getLogger
import java.lang.Thread.sleep
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("UsePropertyAccessSyntax")
@MiskTest(startService = true)
class CronModuleTest {
  @Suppress("unused")
  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      install(FakeLeaseModule())
      install(MiskTestingServiceModule())
      install(LogCollectorModule())

      install(ServiceModule<DependentService>())
      install(
        FakeCronModule(
          ZoneId.of("America/Toronto"),
          dependencies = listOf(DependentService::class.toKey())
        )
      )
      install(CronEntryModule.create<MinuteCron>())
    }
  }

  @Inject private lateinit var logCollector: LogCollector

  @Test fun dependentServicesStartUpBeforeCron() {
    assertThat(logCollector.takeMessages()).containsExactly(
      "DependentService started",
      "CronService started",
      "Adding cron entry misk.cron.MinuteCron, crontab=* * * * *",
    )
  }

  @Singleton
  private class DependentService @Inject constructor() : AbstractIdleService() {
    override fun startUp() {
      logger.info { "DependentService started" }
      sleep(1000)
    }

    override fun shutDown() {}

    companion object {
      val logger = getLogger<DependentService>()
    }
  }
}
