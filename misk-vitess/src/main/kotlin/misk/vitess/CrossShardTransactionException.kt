package misk.vitess

import misk.jdbc.CheckException

/**
 * Thrown when a transaction that spans multiple shards is detected. This can be triggered by cross-shard reads (e.g.
 * queries using lookup vindexes) or cross-shard writes (e.g. inserts/updates targeting rows on different shards).
 *
 * This exception is thrown when the vtgate is configured with `--transaction_mode=SINGLE`, which rejects any
 * transaction that touches more than one shard. Sessions can opt in to cross-shard transactions via
 * `SET transaction_mode = 'multi'`.
 *
 * Multi-shard transactions are not safe and while multiple entity groups may currently reside on the same shard there
 * is no guarantee they will do so across shard splits.
 */
class CrossShardTransactionException @JvmOverloads constructor(message: String? = null, cause: Throwable? = null) :
  CheckException(message, cause)
