package com.squareup.exemplar

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.lang.Thread.sleep
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds
import misk.ReadyService
import misk.ServiceModule
import misk.clustering.weights.FakeClusterWeightModule
import misk.cron.CronEntryModule
import misk.cron.CronPattern
import misk.cron.FakeCronModule
import misk.inject.KAbstractModule
import misk.inject.toKey
import misk.logging.getLogger

class ExemplarCronModule : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<DependentService>().enhancedBy<ReadyService>())
    install(FakeClusterWeightModule())
    install(
      FakeCronModule(
        zoneId = ZoneId.of("America/Toronto"),
        dependencies = listOf(DependentService::class.toKey()),
        installDashboardTab = true,
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
      private val logger = getLogger<DependentService>()
    }
  }

  @Singleton
  @CronPattern("1 * * * *")
  class MinuteCron @Inject constructor() : Runnable {
    var counter = 0

    override fun run() {
      counter++
      logger.info("Minute Cron $counter Start")
      sleep(60.seconds.inWholeMilliseconds)
      logger.info("Minute Cron $counter End")
    }

    companion object {
      private val logger = getLogger<MinuteCron>()
    }
  }
}
