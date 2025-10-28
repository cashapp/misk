package misk.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import org.slf4j.LoggerFactory

class DynamicLogLevelController {
  private val context = LoggerFactory.getILoggerFactory() as LoggerContext
  @Volatile private var currentFilter: LogbackLevelFilter? = null
  @Volatile private var currentLevels: Map<String, Level> = emptyMap()
  private val logger = LoggerFactory.getLogger(DynamicLogLevelController::class.java)

  fun refresh(commaDelimitedPairs: String) {
    val newLevels = DynamicLogParser.parseLoggerToLevelPairs(commaDelimitedPairs)

    // Empty → remove filter
    if (newLevels.isEmpty()) {
      if (currentFilter != null) {
        removeFilter()
      } else {
        logger.debug("Dynamic log levels remain disabled (no filter to remove).")
      }
      return
    }

    // Skip if identical contents
    if (newLevels == currentLevels) {
      logger.debug("Dynamic log levels unchanged; skipping replacement.")
      return
    }

    val newFilter = LogbackLevelFilter(newLevels)
    synchronized(this) {
      currentFilter?.let {
        context.turboFilterList.remove(it)
        logger.info("Removed previous DynamicDebugFilter before applying new levels.")
      }
      context.addTurboFilter(newFilter)
      currentFilter = newFilter
      currentLevels = newLevels
    }

    logger.info("Applied dynamic log levels: ${newLevels.entries.joinToString()}")
  }

  fun clearAll() = removeFilter()

  private fun removeFilter() {
    synchronized(this) {
      currentFilter?.let {
        context.turboFilterList.remove(it)
        logger.info("Removed DynamicDebugFilter (dynamic log levels cleared).")
      } ?: logger.debug("No DynamicDebugFilter found to remove.")
      currentFilter = null
      currentLevels = emptyMap()
    }
  }
}