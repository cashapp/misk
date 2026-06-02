package misk.web.exceptions

import misk.config.Config
import org.slf4j.event.Level

/**
 * Configures the log [Level] for an ActionException.
 *
 * @property client_error_level the level used for 4xx error codes
 * @property server_error_level the level used for 5xx error codes
 */
data class ActionExceptionLogLevelConfig
@JvmOverloads
constructor(val client_error_level: Level = Level.WARN, val server_error_level: Level = Level.ERROR) : Config
