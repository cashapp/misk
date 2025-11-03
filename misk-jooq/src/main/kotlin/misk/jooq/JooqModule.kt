package misk.jooq

import com.google.inject.Provider
import jakarta.inject.Inject
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.inject.toKey
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceService
import misk.jdbc.DataSourceType
import misk.jdbc.DatabasePool
import misk.jdbc.JdbcModule
import misk.jdbc.RealDatabasePool
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
import org.jooq.impl.DefaultExecuteListenerProvider
import org.jooq.impl.DefaultTransactionProvider
import java.time.Clock
import kotlin.reflect.KClass

class JooqModule @JvmOverloads constructor(
  private val qualifier: KClass<out Annotation>,
  private val dataSourceClusterConfig: DataSourceClusterConfig,
  private val jooqCodeGenSchemaName: String,
  private val databasePool: DatabasePool = RealDatabasePool,
  private val readerQualifier: KClass<out Annotation>? = null,
  private val jooqTimestampRecordListenerOptions: JooqTimestampRecordListenerOptions =
    JooqTimestampRecordListenerOptions(install = false),
  private val installHealthChecks: Boolean = true,
  private val installSchemaMigrator: Boolean = true,
  private val jooqConfigExtension: Configuration.() -> Unit = {},
) : KAbstractModule() {

  override fun configure() {
    install(
      JdbcModule(
        qualifier,
        dataSourceClusterConfig.writer,
        readerQualifier,
        dataSourceClusterConfig.reader,
        databasePool,
        installHealthChecks,
        installSchemaMigrator
      )
    )

    bindTransacter(qualifier, dataSourceClusterConfig.writer)
    if (readerQualifier != null && dataSourceClusterConfig.reader != null) {
      bindTransacter(readerQualifier, dataSourceClusterConfig.reader!!)
    }

    val dataSourceServiceProvider = getProvider(keyOf<DataSourceService>(qualifier))
    val jooqTransacterProvider = getProvider(keyOf<JooqTransacter>(qualifier))
    val healthCheckKey = keyOf<HealthCheck>(qualifier)
    bind(healthCheckKey)
      .toProvider(object : Provider<JooqHealthCheck> {
        @Inject lateinit var clock: Clock

        override fun get(): JooqHealthCheck {
          return JooqHealthCheck(
            qualifier,
            dataSourceServiceProvider,
            jooqTransacterProvider,
            clock
          )
        }
      }).asSingleton()
    multibind<HealthCheck>().to(healthCheckKey)
  }

  private fun bindTransacter(
    qualifier: KClass<out Annotation>,
    datasourceConfig: DataSourceConfig,
  ) {
    val transacterKey = JooqTransacter::class.toKey(qualifier)
    val dataSourceServiceProvider = getProvider(keyOf<DataSourceService>(qualifier))
    bind(transacterKey).toProvider(object : Provider<JooqTransacter> {
      @Inject lateinit var clock: Clock

      override fun get(): JooqTransacter {
        return JooqTransacter(
          dataSourceService = dataSourceServiceProvider.get(),
          dataSourceConfig = datasourceConfig,
          jooqCodeGenSchemaName = jooqCodeGenSchemaName,
          jooqTimestampRecordListenerOptions = jooqTimestampRecordListenerOptions,
          clock = clock,
          jooqConfigExtension = jooqConfigExtension
        )
      }
    }).asSingleton()
  }
}

