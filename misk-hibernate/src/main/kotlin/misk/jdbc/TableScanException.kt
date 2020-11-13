package misk.jdbc

import javax.persistence.PersistenceException

/**
 * Exception thrown if we execute a query lacking an index.
 */
class TableScanException(
  message: String? = null,
  cause: Throwable? = null
) : PersistenceException(message, cause)
