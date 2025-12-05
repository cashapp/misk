package misk.logging

import ch.qos.logback.classic.Level
import org.slf4j.LoggerFactory

object DynamicLogParser {
  private val logger = LoggerFactory.getLogger(DynamicLogParser::class.java)

  /**
   * Safely parses a comma-separated string of prefix=level pairs. Returns a validated Map<String, Level>.
   *
   * Example input: "com.squareup.cash.roundtable=TRACE,com.squareup.cash.roundtable.lease=DEBUG"
   *
   * Rules:
   * - Only TRACE and DEBUG levels are accepted
   * - Invalid or malformed entries are ignored (logged as warnings)
   * - Empty or null input → empty map
   */
  fun parseLoggerToLevelPairs(commandDelimitedPairs: String): Map<String, Level> {
    if (commandDelimitedPairs.isBlank()) return emptyMap()

    val parsed = mutableMapOf<String, Level>()

    commandDelimitedPairs
      .split(",")
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .forEach { entry ->
        val parts = entry.split("=", limit = 2)
        if (parts.size != 2) {
          logger.warn("Ignoring malformed entry: '$entry'")
          return@forEach
        }

        val prefix = parts[0].trim()
        val levelStr = parts[1].trim().uppercase()

        if (prefix.isEmpty()) {
          logger.warn("Ignoring empty logger prefix in entry: '$entry'")
          return@forEach
        }

        val level = runCatching { Level.valueOf(levelStr) }.getOrNull()
        if (level == null) {
          logger.warn("Ignoring unknown log level '$levelStr' for prefix '$prefix'")
          return@forEach
        }

        // Only allow TRACE or DEBUG
        if (level != Level.TRACE && level != Level.DEBUG) {
          logger.warn("Ignoring unsupported log level '$levelStr' (only TRACE/DEBUG allowed)")
          return@forEach
        }

        parsed[prefix] = level
      }

    return parsed
  }
}