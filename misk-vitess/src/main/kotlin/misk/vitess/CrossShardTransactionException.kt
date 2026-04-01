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
 * Without two-phase commit (TWOPC), cross-shard transactions use best-effort commit semantics — there is no guarantee
 * of atomicity across shards. Note that even TWOPC only provides atomic commits for writes; it does not provide
 * full ACID cross-shard read isolation.
 *
 * See https://vitess.io/docs/reference/features/distributed-transaction/
 */
class CrossShardTransactionException @JvmOverloads constructor(message: String? = null, cause: Throwable? = null) :
  CheckException(message, cause)
