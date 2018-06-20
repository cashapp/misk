package misk.hibernate

/**
 * Provides explicit block-based transaction demarcation.
 */
interface Transacter {
  /**
   * @returns [true] if the calling thread is currently within a transaction block.
   */
  val inTransaction: Boolean

  /**
   * Executes [lambda] in a transaction. The transaction will be committed once [lambda] completes.
   * If [lambda] raises an exception the transaction will be rolled back.
   *
   * @param lambda a function to execute in a transaction.
   * @return the result of [lambda] function.
   *
   * @throws IllegalStateException if attempting a nested transaction, which is unsupported.
   * @throws Throwable for various other runtime exceptions.
   */
  fun <T> transaction(lambda: (session: Session) -> T): T
}

fun Transacter.shards() = transaction { it.shards() }
fun <T> Transacter.transaction(shard: Shard, lambda: (session: Session) -> T) =
    transaction { it.target(shard) { lambda(it) } }
