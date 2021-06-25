package misk.web.exceptions

import org.slf4j.event.Level
import wisp.config.Config

/**
 * Configures the log [Level] for an ActionException.
 *
 * @property client_error_level the level used for 4xx error codes
 * @property server_error_level the level used for 5xx error codes
 */
data class ActionExceptionLogLevelConfig(
  val client_error_level: Level = Level.WARN,
  val server_error_level: Level = Level.ERROR
) : Config
