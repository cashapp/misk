package misk.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import org.slf4j.LoggerFactory

class DynamicLogLevelController {
  private val context = LoggerFactory.getILoggerFactory() as LoggerContext
  @Volatile private var currentFilter: LogLevelFilter? = null
  private val logger = LoggerFactory.getLogger(DynamicLogLevelController::class.java)

  fun refresh(commaDelimitedPairs: String) {
    val newLevels = DynamicLogParser.parseLoggerToLevelPairs(commaDelimitedPairs)

    synchronized(this) {
      // Empty → remove filter if exists
      if (newLevels.isEmpty()) {
        if (currentFilter != null) {
          context.turboFilterList.remove(currentFilter)
          logger.info("Removed DynamicDebugFilter (dynamic log levels cleared).")
          currentFilter = null
        } else {
          logger.debug("No filter to remove (already empty).")
        }
        return
      }

      // Skip if identical contents
      if (currentFilter?.dynamicLevels == newLevels) {
        logger.debug("Dynamic log levels unchanged, skipping replacement: ${newLevels.entries.joinToString()}")
        return
      }

      // Remove old filter if exists
      currentFilter?.let {
        context.turboFilterList.remove(it)
        logger.info("Removed previous DynamicDebugFilter before applying new levels.")
      }

      // Add new filter
      val newFilter = LogLevelFilter(newLevels)
      context.addTurboFilter(newFilter)
      currentFilter = newFilter
      logger.info("Applied dynamic log levels: ${newLevels.entries.joinToString()}")
    }
  }

}