package misk.cron

import misk.MiskTestingServiceModule
import misk.clustering.fake.lease.FakeLeaseModule
import misk.inject.KAbstractModule
import java.time.ZoneId

class CronTestingModule : KAbstractModule() {
  override fun configure() {
    val applicationModules: List<KAbstractModule> = listOf(
      FakeLeaseModule(),
      MiskTestingServiceModule(),

      // Cron support requires registering the CronJobHandler and the CronRunnerModule.
      FakeCronModule(ZoneId.of("America/Toronto"))
    )

    applicationModules.forEach { module -> install(module) }
  }
}
