package misk.jooq

import com.google.inject.Injector
import com.google.inject.Provider
import jakarta.inject.Inject
import java.time.Clock
import kotlin.reflect.KClass
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.inject.toKey
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceService
import misk.jdbc.DatabasePool
import misk.jdbc.JdbcModule
import misk.jdbc.RealDatabasePool
import misk.jooq.config.CachedConfigurationFactory
import misk.jooq.config.ConfigurationFactory
import misk.jooq.listeners.JooqTimestampRecordListenerOptions
import org.jooq.Configuration

class JooqModule
@JvmOverloads
constructor(
  private val qualifier: KClass<out Annotation>,
  private val dataSourceClusterConfig: DataSourceClusterConfig,
  private val jooqCodeGenSchemaName: String,
  private val databasePool: DatabasePool = RealDatabasePool,
  private val readerQualifier: KClass<out Annotation>? = null,
  private val jooqTimestampRecordListenerOptions: JooqTimestampRecordListenerOptions =
    JooqTimestampRecordListenerOptions(install = false),
  private val installHealthChecks: Boolean = true,
  private val installSchemaMigrator: Boolean = true,
  private val jdbcModuleAlreadySetup: Boolean = false,
  private val jooqConfigExtension: Configuration.() -> Unit = {},
) : KAbstractModule() {

  override fun configure() {
    if (!jdbcModuleAlreadySetup) {
      install(
        JdbcModule(
          qualifier = qualifier,
          config = dataSourceClusterConfig.writer,
          readerQualifier = readerQualifier,
          readerConfig = dataSourceClusterConfig.reader,
          databasePool = databasePool,
          installHealthCheck = installHealthChecks,
          installSchemaMigrator = installSchemaMigrator,
        )
      )
    }

    bind<ConfigurationFactory>()
      .annotatedWith(qualifier.java)
      .toProvider(
        CachedConfigurationFactoryProvider(
          dataSourceConfig = dataSourceClusterConfig.writer,
          jooqCodeGenSchemaName = jooqCodeGenSchemaName,
          jooqTimestampRecordListenerOptions = jooqTimestampRecordListenerOptions,
          jooqConfigExtension = jooqConfigExtension,
          qualifier = qualifier,
        )
      )
      .asSingleton()
    bindTransacter(qualifier, dataSourceClusterConfig.writer)
    if (readerQualifier != null && dataSourceClusterConfig.reader != null) {
      bind<ConfigurationFactory>()
        .annotatedWith(readerQualifier.java)
        .toProvider(
          CachedConfigurationFactoryProvider(
            dataSourceConfig = dataSourceClusterConfig.reader!!,
            jooqCodeGenSchemaName = jooqCodeGenSchemaName,
            jooqTimestampRecordListenerOptions = jooqTimestampRecordListenerOptions,
            jooqConfigExtension = jooqConfigExtension,
            qualifier = readerQualifier,
          )
        )
        .asSingleton()
      bindTransacter(readerQualifier, dataSourceClusterConfig.reader!!)
    }

    val healthCheckKey = keyOf<HealthCheck>(qualifier)
    bind(healthCheckKey).toProvider(JooqHealthCheckProvider(qualifier)).asSingleton()
    multibind<HealthCheck>().to(healthCheckKey)
  }

  private fun bindTransacter(qualifier: KClass<out Annotation>, dataSourceConfig: DataSourceConfig) {
    val configurationFactoryProvider = getProvider(keyOf<ConfigurationFactory>(qualifier))
    val transacterKey = JooqTransacter::class.toKey(qualifier)
    bind(transacterKey)
      .toProvider(
        Provider {
          JooqTransacter(
            configurationFactory = { options -> configurationFactoryProvider.get().getConfiguration(options) },
            dataSourceType = dataSourceConfig.type,
          )
        }
      )
      .asSingleton()
  }

  private class JooqHealthCheckProvider(private val qualifier: KClass<out Annotation>) : Provider<JooqHealthCheck> {
    @Inject private lateinit var clock: Clock

    @Inject private lateinit var injector: Injector

    override fun get(): JooqHealthCheck {
      val dataSourceServiceProvider = injector.getProvider(keyOf<DataSourceService>(qualifier))
      val jooqTransacterProvider = injector.getProvider(keyOf<JooqTransacter>(qualifier))
      return JooqHealthCheck(
        qualifier = qualifier,
        dataSourceProvider = dataSourceServiceProvider,
        jooqTransacterProvider = jooqTransacterProvider,
        clock = clock,
      )
    }
  }

  private class CachedConfigurationFactoryProvider(
    private val dataSourceConfig: DataSourceConfig,
    private val jooqCodeGenSchemaName: String,
    private val jooqTimestampRecordListenerOptions: JooqTimestampRecordListenerOptions,
    private val jooqConfigExtension: Configuration.() -> Unit,
    private val qualifier: KClass<out Annotation>,
  ) : Provider<CachedConfigurationFactory> {
    @Inject private lateinit var clock: Clock

    @Inject private lateinit var injector: Injector

    override fun get(): CachedConfigurationFactory {
      val readerDataSourceServiceProvider = injector.getProvider(keyOf<DataSourceService>(qualifier))
      return CachedConfigurationFactory(
        clock = clock,
        dataSourceConfig = dataSourceConfig,
        dataSourceService = readerDataSourceServiceProvider.get(),
        jooqCodeGenSchemaName = jooqCodeGenSchemaName,
        jooqTimestampRecordListenerOptions = jooqTimestampRecordListenerOptions,
        jooqConfigExtension = jooqConfigExtension,
      )
    }
  }
}
