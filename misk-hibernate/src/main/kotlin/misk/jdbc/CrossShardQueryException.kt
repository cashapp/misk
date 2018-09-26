package misk.jdbc

import javax.persistence.PersistenceException

class CrossShardQueryException(
  message: String? = null,
  cause: Throwable? = null
) : PersistenceException(message, cause)