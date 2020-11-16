package misk.jdbc

import java.sql.Connection
import javax.sql.DataSource

interface Transacter {
  /**
   * Returns true if the calling thread is currently within a transaction block.
   */
  val inTransaction: Boolean

  /**
   * Starts a transaction on the current thread, executes [work], and commits the transaction.
   * If the work raises an exception the transaction will be rolled back instead of committed.
   *
   * It is an error to start a transaction if another transaction is already in progress.
   */
  fun <T> transaction(work: (connection: Connection) -> T): T
}

class RealTransacter constructor(private val dataSource: DataSource) : Transacter {
  private val transacting = ThreadLocal.withInitial { false }

  override val inTransaction: Boolean get() = transacting.get()

  override fun <T> transaction(work: (connection: Connection) -> T): T {
    check(!transacting.get()) { "The current thread is already in a transaction" }
    transacting.set(true)

    try {
      return dataSource.connection.use { connection ->
        /*
         * We are using Hikari, which will automatically roll back incomplete transactions.
         * This means there's actually no need to wrap the transaction in a try clause to
         * do rollback on exception
         */

        // BEGIN
        if (connection.autoCommit) {
          connection.autoCommit = false
        }
        // Do stuff
        val result = work(connection)
        // COMMIT
        connection.commit()

        result
      }
    } finally {
      transacting.set(false)
    }
  }
}