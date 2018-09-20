package misk.hibernate

import org.hibernate.HibernateException

class CrossShardTransactionException(
  message: String? = null,
  cause: Throwable? = null
) : HibernateException(message, cause)