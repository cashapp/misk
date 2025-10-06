package misk.jooq

import misk.backoff.ExponentialBackoff
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceService
import misk.jdbc.DataSourceType
import misk.jooq.listeners.AvoidUsingSelectStarListener
import misk.jooq.listeners.JooqSQLLogger
import misk.jooq.listeners.JooqTimestampRecordListener
import misk.jooq.listeners.JooqTimestampRecordListenerOptions
import org.jooq.Configuration
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.MappedSchema
import org.jooq.conf.RenderMapping
import org.jooq.conf.Settings
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultExecuteListenerProvider
import org.jooq.impl.DefaultTransactionProvider
import misk.logging.getLogger
import java.sql.SQLException
import java.sql.SQLRecoverableException
import java.sql.SQLTransientException
import java.time.Clock
import java.time.Duration
import javax.persistence.OptimisticLockException

class JooqTransacter @JvmOverloads constructor(
  private val dataSourceService: DataSourceService,
  private val dataSourceConfig: DataSourceConfig,
  private val jooqCodeGenSchemaName: String,
  private val jooqTimestampRecordListenerOptions: JooqTimestampRecordListenerOptions =
    JooqTimestampRecordListenerOptions(install = false),
  private val clock: Clock,
  private val jooqConfigExtension: Configuration.() -> Unit = {}
) {

  @JvmOverloads
  fun <RETURN_TYPE> transaction(
    options: TransacterOptions = TransacterOptions(),
    callback: (jooqSession: JooqSession) -> RETURN_TYPE
  ): RETURN_TYPE {
    val backoff = ExponentialBackoff(Duration.ofMillis(10L), Duration.ofMillis(options.maxRetryDelayMillis))
    var attempt = 0

    while (true) {
      try {
        return performInTransaction(options, callback, ++attempt)
      } catch (e: Exception) {
        if (!isRetryable(e)) throw e

        if (attempt >= options.maxAttempts) {
          log.info {
              "jooq recoverable transaction exception (attempt $attempt), no more attempts"
          }
          throw e
        }

        val sleepDuration = backoff.nextRetry()
        log.info(e) {
            "jooq recoverable transaction exception (attempt $attempt), will retry after a $sleepDuration delay"
        }

        if (!sleepDuration.isZero) {
          Thread.sleep(sleepDuration.toMillis())
        }
      }
    }
  }

  private fun <RETURN_TYPE> performInTransaction(
    options: TransacterOptions,
    callback: (jooqSession: JooqSession) -> RETURN_TYPE,
    attempt: Int,
  ): RETURN_TYPE {
    return try {
      val result = createDSLContextAndCallback(options, callback)
      if (attempt > 1) {
        log.info {
          "Retried jooq transaction succeeded after [attempts=$attempt]"
        }
      }
      result
    } catch (e: Exception) {
      if (attempt >= options.maxAttempts) {
        log.warn(e) {
          "Recoverable transaction exception [attempts=$attempt], no more attempts"
        }
        throw e
      }
      log.info(e) { "Exception thrown while transacting with the db via jooq" }
      throw e
    }
  }

  private fun <RETURN_TYPE> createDSLContextAndCallback(
    options: TransacterOptions,
    callback: (jooqSession: JooqSession) -> RETURN_TYPE
  ): RETURN_TYPE {
    var jooqSession: JooqSession? = null
    return try {
      dslContext(dataSourceService, clock, dataSourceConfig, options).transactionResult { configuration ->
        jooqSession = JooqSession(DSL.using(configuration))
        runCatching { callback(jooqSession).also { jooqSession.executePreCommitHooks() } }
          .onFailure { jooqSession.onSessionClose { jooqSession.executeRollbackHooks(it) } }
          .getOrElse { throw it } // JooqExtensions.kt shadows Result<T>.getOrThrow()... This is equivalent.
      }.also { jooqSession?.executePostCommitHooks() }
    } finally {
      jooqSession?.executeSessionCloseHooks()
    }
  }

  private fun dslContext(
    dataSourceService: DataSourceService,
    clock: Clock,
    datasourceConfig: DataSourceConfig,
    options: TransacterOptions,
  ): DSLContext {
    val settings = Settings()
      .withExecuteWithOptimisticLocking(true)
      .withRenderMapping(
        RenderMapping().withSchemata(
          MappedSchema()
            .withInput(jooqCodeGenSchemaName)
            .withOutput(datasourceConfig.database),
        ),
      )

    val connectionProvider = IsolationLevelAwareConnectionProvider(
      dataSourceConnectionProvider = DataSourceConnectionProvider(dataSourceService.dataSource),
      transacterOptions = options,
    )

    return DSL.using(connectionProvider, datasourceConfig.type.toSqlDialect(), settings)
      .apply {
        configuration().set(
          DefaultTransactionProvider(
            configuration().connectionProvider(),
            false,
          ),
        ).apply {
          val executeListeners = mutableListOf(
            DefaultExecuteListenerProvider(AvoidUsingSelectStarListener()),
          )
          if ("true" == datasourceConfig.show_sql) {
            executeListeners.add(DefaultExecuteListenerProvider(JooqSQLLogger()))
          }
          set(*executeListeners.toTypedArray())

          if (jooqTimestampRecordListenerOptions.install) {
            set(
              JooqTimestampRecordListener(
                clock = clock,
                createdAtColumnName = jooqTimestampRecordListenerOptions.createdAtColumnName,
                updatedAtColumnName = jooqTimestampRecordListenerOptions.updatedAtColumnName,
              ),
            )
          }
        }.apply(jooqConfigExtension)
      }
  }

  private fun DataSourceType.toSqlDialect() = when (this) {
    DataSourceType.MYSQL -> SQLDialect.MYSQL
    DataSourceType.HSQLDB -> SQLDialect.HSQLDB
    DataSourceType.VITESS_MYSQL -> SQLDialect.MYSQL
    DataSourceType.POSTGRESQL -> SQLDialect.POSTGRES
    DataSourceType.TIDB -> SQLDialect.MYSQL
    DataSourceType.COCKROACHDB -> throw IllegalArgumentException("CockroachDB is not supported in JOOQ")
    else -> throw IllegalArgumentException("no SQLDialect for " + this.name)
  }

  /**
   * Determines if an exception should trigger a transaction retry.
   * Adapted from Hibernate RealTransacter exception handling logic (without CockroachDB support).
   */
  private fun isRetryable(th: Throwable): Boolean {
    return when (th) {
      is SQLRecoverableException,
      is SQLTransientException,
      is OptimisticLockException -> true
      is DataAccessException -> {
        // Check if the underlying cause is retryable
        val cause = th.cause
        if (cause is SQLException) {
          isMessageRetryable(cause) || isCauseRetryable(th)
        } else {
          // For DataAccessException without SQLException cause, always retry to match original behavior
          true
        }
      }
      is SQLException -> if (isMessageRetryable(th)) true else isCauseRetryable(th)
      else -> isCauseRetryable(th)
    }
  }

  private fun isMessageRetryable(th: SQLException) =
    isConnectionClosed(th) ||
      isVitessTransactionNotFound(th) ||
      isTidbWriteConflict(th)

  /**
   * This is thrown as a raw SQLException from Hikari even though it is most certainly a recoverable exception. See
   * com/zaxxer/hikari/pool/ProxyConnection.java:493
   */
  private fun isConnectionClosed(th: SQLException) = th.message.equals("Connection is closed")

  /**
   * We get this error as a MySQLQueryInterruptedException when a tablet gracefully terminates, we just need to retry
   * the transaction and the new primary should handle it.
   *
   * ```
   * vttablet: rpc error: code = Aborted desc = transaction 1572922696317821557:
   * not found (CallerID: )
   * ```
   */
  private fun isVitessTransactionNotFound(th: SQLException): Boolean {
    val message = th.message
    return message != null &&
      message.contains("vttablet: rpc error") &&
      message.contains("code = Aborted") &&
      message.contains("transaction") &&
      message.contains("not found")
  }

  /**
   * "Transactions in TiKV encounter write conflicts". This can happen when optimistic transaction mode is on. Conflicts
   * are detected during transaction commit https://docs.pingcap.com/tidb/dev/tidb-faq#error-9007-hy000-write-conflict
   */
  private fun isTidbWriteConflict(th: SQLException): Boolean {
    return th.errorCode == 9007
  }

  private fun isCauseRetryable(th: Throwable) = th.cause?.let { isRetryable(it) } ?: false

  data class TransacterOptions @JvmOverloads constructor(
    val maxAttempts: Int = 3,
    val maxRetryDelayMillis: Long = 500,
    val isolationLevel: TransactionIsolationLevel = TransactionIsolationLevel.REPEATABLE_READ
  )

  companion object {
    private val log = getLogger<JooqTransacter>()

    val noRetriesOptions: TransacterOptions
      get() = TransacterOptions().copy(maxAttempts = 1)
  }
}
