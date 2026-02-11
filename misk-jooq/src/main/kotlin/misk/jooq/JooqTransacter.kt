package misk.jooq

import misk.backoff.ExponentialBackoff
import misk.backoff.RetryConfig
import misk.backoff.retry
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceService
import misk.jdbc.DataSourceType
import misk.jdbc.retry.ExceptionClassifier
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
import org.jooq.impl.DSL
import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultExecuteListenerProvider
import org.jooq.impl.DefaultTransactionProvider
import misk.logging.getLogger
import java.time.Clock

class JooqTransacter @JvmOverloads constructor(
  private val dataSourceService: DataSourceService,
  private val dataSourceConfig: DataSourceConfig,
  private val jooqCodeGenSchemaName: String,
  private val jooqTimestampRecordListenerOptions: JooqTimestampRecordListenerOptions =
    JooqTimestampRecordListenerOptions(install = false),
  private val clock: Clock,
  private val jooqConfigExtension: Configuration.() -> Unit = {}
) {
  
  private val exceptionClassifier: ExceptionClassifier = JooqExceptionClassifier(dataSourceConfig.type)

  @JvmOverloads
  fun <RETURN_TYPE> transaction(
    options: TransacterOptions = TransacterOptions(),
    callback: (jooqSession: JooqSession) -> RETURN_TYPE
  ): RETURN_TYPE {
    val backoff = ExponentialBackoff(
      baseDelay = java.time.Duration.ofMillis(options.minRetryDelayMillis),
      maxDelay = java.time.Duration.ofMillis(options.maxRetryDelayMillis),
      jitter = java.time.Duration.ofMillis(options.retryJitterMillis)
    )
    val retryConfig = RetryConfig.Builder(options.maxAttempts, backoff)
      .shouldRetry { exceptionClassifier.isRetryable(it) }
      .onRetry { attempt, e -> log.info(e) { "jooq transaction failed, retrying (attempt $attempt)" } }
      .build()
    return retry(retryConfig) { performInTransaction(options, callback) }
  }

  private fun <RETURN_TYPE> performInTransaction(
    options: TransacterOptions,
    callback: (jooqSession: JooqSession) -> RETURN_TYPE
  ): RETURN_TYPE {
    return createDSLContextAndCallback(options, callback)
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
    else -> throw IllegalArgumentException("no SQLDialect for " + this.name)
  }



  data class TransacterOptions @JvmOverloads constructor(
    val maxAttempts: Int = 3,
    val minRetryDelayMillis: Long = 10,
    val maxRetryDelayMillis: Long = 500,
    val retryJitterMillis: Long = 400,
    val isolationLevel: TransactionIsolationLevel = TransactionIsolationLevel.REPEATABLE_READ
  )

  companion object {
    private val log = getLogger<JooqTransacter>()

    val noRetriesOptions: TransacterOptions
      get() = TransacterOptions().copy(maxAttempts = 1)
  }
}
