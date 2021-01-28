package misk.jdbc

import java.sql.SQLException

/**
 * An exception that is thrown when a [Check] fails.
 */
open class CheckException(
  message: String? = null,
  cause: Throwable? = null
): SQLException(message, cause)

