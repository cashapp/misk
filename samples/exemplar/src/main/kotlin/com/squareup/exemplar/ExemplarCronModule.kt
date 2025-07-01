package com.squareup.exemplar

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.clustering.fake.lease.FakeLeaseModule
import misk.clustering.weights.FakeClusterWeightModule
import misk.cron.CronEntryModule
import misk.cron.CronPattern
import misk.cron.FakeCronModule
import misk.inject.KAbstractModule
import misk.inject.toKey
import wisp.logging.getLogger
import java.lang.Thread.sleep
import java.time.ZoneId

class ExemplarCronModule : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<DependentService>().enhancedBy<ReadyService>())
    install(FakeClusterWeightModule())
    install(
      FakeCronModule(
        ZoneId.of("America/Toronto"),
        dependencies = listOf(DependentService::class.toKey())
      )
    )
    install(CronEntryModule.create<MinuteCron>())
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

  @Singleton
  @CronPattern("* * * * *")
  class MinuteCron @Inject constructor() : Runnable {
    var counter = 0

    override fun run() {
      counter++
      log.info("Minute Cron $counter Start")
      sleep(10_000)
      log.info("Minute Cron $counter End")
    }

    companion object {
      val log = getLogger<MinuteCron>()
    }
  }
}
