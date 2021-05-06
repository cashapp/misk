package misk.eventrouter

import com.google.common.util.concurrent.AbstractIdleService
import wisp.logging.getLogger
import javax.inject.Inject
import javax.inject.Singleton

private val logger = getLogger<EventRouterService>()

@Singleton
internal class EventRouterService : AbstractIdleService() {
  @Inject lateinit var realEventRouter: RealEventRouter

  override fun startUp() {
    logger.info("starting up EventRouter service")
    realEventRouter.joinCluster()
  }

  override fun shutDown() {
    logger.info("shutting down up EventRouter service")
    realEventRouter.leaveCluster()
  }
}
