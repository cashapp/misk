package misk.jdbc

/**
 * Exception thrown if we execute a query lacking an index.
 */
class TableScanException(
  message: String? = null,
  cause: Throwable? = null
) : CheckException(message, cause)