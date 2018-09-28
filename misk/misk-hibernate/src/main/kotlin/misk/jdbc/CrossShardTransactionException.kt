package misk.jdbc

import javax.persistence.PersistenceException

class CrossShardTransactionException(
  message: String? = null,
  cause: Throwable? = null
) : PersistenceException(message, cause)