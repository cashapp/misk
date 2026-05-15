package misk.jooq.transacter

import java.time.Duration
import misk.jooq.JooqSession
import misk.jooq.JooqTransacter
import misk.jooq.TransactionIsolationLevel

/**
 * Implements [Transacter] using [ThreadLocal] for thread safety of the transaction options and current transaction
 * state
 */
internal class RealTransacter(
  private val defaultOptions: JooqTransacter.TransacterOptions,
  private val writerJooqTransacter: JooqTransacter,
  private val readerJooqTransacter: JooqTransacter?,
) : Transacter {
  private val threadTxnOptions = ThreadLocal.withInitial { defaultOptions }
  private val threadInTransaction = ThreadLocal.withInitial { false }

  override val inTransaction: Boolean
    get() = threadInTransaction.get()

  override fun <T> transaction(closure: (session: JooqSession) -> T): T =
    internalTransaction(writerJooqTransacter, closure)

  override fun <T> replicaRead(closure: (session: JooqSession) -> T): T {
    checkNotNull(readerJooqTransacter) {
      "No reader is configured for replica reads. Pass in both a writer and reader qualifier into the JooqModule"
    }
    if (!options.readOnly) {
      threadTxnOptions.set(options.copy(readOnly = true))
    }
    return internalTransaction(readerJooqTransacter, closure)
  }

  override fun maxRetries(maxRetries: Int): Transacter {
    threadTxnOptions.set(options.copy(maxRetries = maxRetries))
    return this
  }

  override fun noRetries(): Transacter {
    threadTxnOptions.set(options.copy(maxRetries = 0))
    return this
  }

  override fun maxRetryDelay(duration: Duration): Transacter {
    threadTxnOptions.set(options.copy(maxRetryDelayMillis = duration.toMillis()))
    return this
  }

  override fun isolationLevel(level: TransactionIsolationLevel): Transacter {
    threadTxnOptions.set(options.copy(isolationLevel = level))
    return this
  }

  override fun writable(): Transacter {
    threadTxnOptions.set(options.copy(readOnly = false))
    return this
  }

  override fun readOnly(): Transacter {
    threadTxnOptions.set(options.copy(readOnly = true))
    return this
  }

  internal val options: JooqTransacter.TransacterOptions
    get() = threadTxnOptions.get()

  private inline fun <T> internalTransaction(
    transacter: JooqTransacter,
    crossinline closure: (session: JooqSession) -> T,
  ): T {
    // jOOQ does support nested txns via JDBC savepoints, but this Transacter needs a less naive ThreadLocal
    // implementation to accommodate that
    check(!inTransaction) { "Nested transactions are not currently supported" }
    return try {
      transacter.transaction(options) {
        threadInTransaction.set(true)
        closure(it)
      }
    } finally {
      threadInTransaction.set(false)
      threadTxnOptions.remove()
    }
  }
}
