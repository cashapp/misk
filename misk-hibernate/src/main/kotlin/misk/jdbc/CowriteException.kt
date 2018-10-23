package misk.jdbc

import javax.persistence.PersistenceException

/**
 * Thrown when a transaction that writes to multiple entity groups is detected.
 *
 * Multi shard transactions are not safe and while multiple entity groups may currently reside on
 * the same shard there is no guarantee they will do so across shard splits.
 *
 * A transaction never spans a shard split. That is they never start before the split and end after,
 * either they commit before the split or they start after the split. That said a transaction may
 * start before a shard split and then time out or cancel and be retried after the shard split. If
 * two entity groups resided on the same shard before the transaction they may no longer do so when
 * the transaction is retried.
 */
class CowriteException(
  message: String? = null,
  cause: Throwable? = null
) : PersistenceException(message, cause)