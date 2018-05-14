package misk.hibernate

import com.google.common.util.concurrent.AbstractIdleService
import misk.logging.getLogger
import javax.inject.Singleton

private val logger = getLogger<HibernateService>()

@Singleton
internal class HibernateService : AbstractIdleService() {
  override fun startUp() {
    logger.info("starting up Hibernate")
  }

  override fun shutDown() {
    logger.info("shutting down Hibernate")
  }
}
