package misk.cron

import misk.MiskTestingServiceModule
import misk.clustering.fake.lease.FakeLeaseModule
import misk.inject.KAbstractModule
import misk.jobqueue.FakeJobHandlerModule
import misk.jobqueue.FakeJobQueueModule
import misk.jobqueue.QueueName
import java.time.ZoneId

class CronTestingModule : KAbstractModule() {
  private val queueName = QueueName("misk.cron.jobqueue")

  override fun configure() {
    val applicationModules: List<KAbstractModule> = listOf(
      FakeLeaseModule(),
      FakeJobQueueModule(),
      MiskTestingServiceModule(),

      // Cron support requires registering the CronJobHandler and the CronRunnerModule.
      FakeJobHandlerModule.create<CronJobHandler>(queueName),
      CronModule(ZoneId.of("America/Toronto"), queueName)
    )

    applicationModules.forEach { module -> install(module) }
  }
}
