package misk.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.core.spi.FilterReply
import org.slf4j.Marker

class LogbackLevelFilter(val dynamicLevels: Map<String, Level>) : TurboFilter() {

  override fun decide(
    marker: Marker?,
    logger: ch.qos.logback.classic.Logger,
    level: Level,
    format: String?,
    params: Array<Any>?,
    t: Throwable?,
  ): FilterReply {
    // Never interfere with INFO or higher.
    if (level.isGreaterOrEqual(Level.INFO)) return FilterReply.NEUTRAL

    // Try exact match first
    dynamicLevels[logger.name]?.let { target ->
      return if (level.isGreaterOrEqual(target)) FilterReply.ACCEPT else FilterReply.NEUTRAL
    }

    // Try prefix match (find most specific matching prefix)
    val target =
      dynamicLevels.entries.filter { logger.name.startsWith(it.key) }.maxByOrNull { it.key.length }?.value
        ?: return FilterReply.NEUTRAL

    return if (level.isGreaterOrEqual(target)) FilterReply.ACCEPT else FilterReply.NEUTRAL
  }
}