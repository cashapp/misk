package misk.hibernate

import org.slf4j.event.Level
import misk.config.Config

/**
 * Configures the log [Level] for a HibernateException.
 *
 * @property log_level
 */

data class HibernateExceptionLogLevelConfig @JvmOverloads constructor(
  val log_level: Level = Level.WARN
) : Config
