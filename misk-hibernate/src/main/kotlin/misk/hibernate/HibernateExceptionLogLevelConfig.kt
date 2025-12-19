package misk.hibernate

import misk.config.Config
import org.slf4j.event.Level

/**
 * Configures the log [Level] for a HibernateException.
 *
 * @property log_level
 */
data class HibernateExceptionLogLevelConfig @JvmOverloads constructor(val log_level: Level = Level.WARN) : Config
