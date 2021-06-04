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
   *
   * Prefer using [transactionWithSession] instead of this method as it has more functionality such
   * as commit hooks.
   */
  @Deprecated("Use transactionWithSession instead",
    replaceWith = ReplaceWith("transactionWithSession(work)")
  )
  fun <T> transaction(work: (connection: Connection) -> T): T

  /**
   * Starts a transaction on the current thread, executes [work], and commits the transaction.
   * If the work raises an exception the transaction will be rolled back instead of committed.
   *
   * This session object passed in wraps a connection and provides a way to add pre and post commit
   * hooks that execute before and after a transaction is committed.
   *
   * It is an error to start a transaction if another transaction is already in progress.
   */
  fun <T> transactionWithSession(work: (session: JDBCSession) -> T): T
}

class RealTransacter constructor(private val dataSource: DataSource) : Transacter {
  private val transacting = ThreadLocal.withInitial { false }

  override val inTransaction: Boolean get() = transacting.get()

  override fun <T> transaction(work: (connection: Connection) -> T): T =
    transactionWithSession { session -> session.useConnection(work) }


  override fun <T> transactionWithSession(work: (session: JDBCSession) -> T): T {
    check(!transacting.get()) { "The current thread is already in a transaction" }
    transacting.set(true)

    var session: JDBCSession? = null
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

        session = JDBCSession(connection)
        val result = work(session!!)
        // COMMIT
        session!!.executePreCommitHooks()
        connection.commit()
        session!!.executePostCommitHooks()
        result
      }
    } finally {
      transacting.set(false)
      session?.executeSessionCloseHooks()
    }
  }
}
