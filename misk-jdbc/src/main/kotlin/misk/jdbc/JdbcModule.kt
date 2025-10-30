package misk.jdbc

import com.google.inject.Provider
import io.opentracing.Tracer
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import misk.ReadyService
import misk.ServiceModule
import misk.database.StartDatabaseService
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.inject.setOfType
import misk.inject.toKey
import misk.resources.ResourceLoader
import wisp.deployment.Deployment
import javax.sql.DataSource
import kotlin.reflect.KClass

/**
 * Binds database connectivity for a qualified data source. This binds the following public types:
 *
 *  * @Qualifier [javax.sql.DataSource]
 *  * @Qualifier [misk.jdbc.DataSourceConfig]
 *
 * [DataSource.getConnection] can be used to get JDBC connections to your database.
 *
 * This also registers services to connect to the database ([DataSourceService]) and to verify
 * that the schema is up-to-date ([SchemaMigratorService]).
 */
class JdbcModule @JvmOverloads constructor(
  private val qualifier: KClass<out Annotation>,
  config: DataSourceConfig,
  private val readerQualifier: KClass<out Annotation>?,
  readerConfig: DataSourceConfig?,
  val databasePool: DatabasePool = RealDatabasePool,
  private val installHealthCheck: Boolean = true,
  private val installSchemaMigrator: Boolean = true,
) : KAbstractModule() {
  val config = config.withDefaults()
  val readerConfig = readerConfig?.withDefaults()

  constructor(
    qualifier: KClass<out Annotation>,
    config: DataSourceConfig,
    databasePool: DatabasePool = RealDatabasePool,
  ) : this(qualifier, config, null, null, databasePool)

  constructor(
    qualifier: KClass<out Annotation>,
    config: DataSourceConfig,
    databasePool: DatabasePool = RealDatabasePool,
    installSchemaMigrator: Boolean = true,
  ) : this(qualifier, config, null, null, databasePool, true, installSchemaMigrator)

  override fun configure() {
    if (readerQualifier != null) {
      check(readerConfig != null) {
        "Reader not configured for datasource $readerQualifier"
      }
    }

    // Bind StartDatabaseService.
    install(ServiceModule<StartDatabaseService>(qualifier))
    bind(
      keyOf<StartDatabaseService>(qualifier)
    ).toProvider(object : Provider<StartDatabaseService> {
      @Inject
      lateinit var deployment: Deployment
      override fun get(): StartDatabaseService {
        return StartDatabaseService(deployment = deployment, config = config, qualifier = qualifier)
          .init()
      }
    }).asSingleton()

    bindDataSource(qualifier, config, true)
    if (readerQualifier != null && readerConfig != null) {
      bindDataSource(readerQualifier, readerConfig, false)
    }

    newMultibinder<DataSourceDecorator>(qualifier)

    // Only bind SchemaMigrator-related services if installSchemaMigrator is true
    // and migrations are not externally managed.
    if (installSchemaMigrator && config.migrations_format != MigrationsFormat.EXTERNALLY_MANAGED) {
      val schemaMigratorKey = SchemaMigrator::class.toKey(qualifier)
      val schemaMigratorProvider = getProvider(schemaMigratorKey)
      val connectorProvider = getProvider(keyOf<DataSourceConnector>(qualifier))
      val skeemaWrapperKey = SkeemaWrapper::class.toKey(qualifier)
      val skeemaWrapperProvider = getProvider(skeemaWrapperKey)

      bind(skeemaWrapperKey).toProvider(object : Provider<SkeemaWrapper> {
        @Inject
        lateinit var resourceLoader: ResourceLoader
        override fun get(): SkeemaWrapper = SkeemaWrapper(
          qualifier = qualifier,
          resourceLoader = resourceLoader,
          dataSourceConfig = config,
        )
      }).asSingleton()

      val dataSourceServiceProvider = getProvider(keyOf<DataSourceService>(qualifier))
      bind(schemaMigratorKey).toProvider(object : Provider<SchemaMigrator> {
        @Inject
        lateinit var resourceLoader: ResourceLoader
        override fun get(): SchemaMigrator = when (config.migrations_format) {
          MigrationsFormat.TRADITIONAL ->
            TraditionalSchemaMigrator(
              qualifier = qualifier,
              resourceLoader = resourceLoader,
              dataSourceConfig = config,
              dataSourceService = dataSourceServiceProvider.get(),
              connector = connectorProvider.get()
            )
          MigrationsFormat.DECLARATIVE ->
            DeclarativeSchemaMigrator(
              resourceLoader = resourceLoader,
              dataSourceService = dataSourceServiceProvider.get(),
              connector = connectorProvider.get(),
              skeemaWrapper = skeemaWrapperProvider.get(),
            )
          MigrationsFormat.EXTERNALLY_MANAGED ->
            throw IllegalStateException("SchemaMigrator should not be created for externally managed migrations")
        }
      }).asSingleton()

      val schemaMigratorServiceKey = keyOf<SchemaMigratorService>(qualifier)
      bind(schemaMigratorServiceKey)
        .toProvider(object : Provider<SchemaMigratorService> {
          @Inject
          lateinit var deployment: Deployment
          override fun get(): SchemaMigratorService = SchemaMigratorService(
            qualifier = qualifier,
            deployment = deployment,
            schemaMigratorProvider = schemaMigratorProvider,
            connectorProvider = connectorProvider
          )
        }).asSingleton()

      // Install SchemaMigratorService
      install(
        ServiceModule<SchemaMigratorService>(qualifier)
          .dependsOn<DataSourceService>(qualifier)
          .enhancedBy<ReadyService>()
      )
      
      if (installHealthCheck) {
        multibind<HealthCheck>().to(schemaMigratorServiceKey)
      }
    }
  }

  private fun bindDataSource(
    qualifier: KClass<out Annotation>,
    config: DataSourceConfig,
    isWriter: Boolean,
  ) {
    // These items are configured on the writer qualifier only
    val dataSourceDecoratorsKey = setOfType(DataSourceDecorator::class).toKey(this.qualifier)

    val deploymentProvider: Provider<Deployment> = getProvider(keyOf<Deployment>())

    bind(keyOf<DataSourceConfig>(qualifier)).toInstance(config)

    // Bind PingDatabaseService.
    if (installHealthCheck) {
      bind(keyOf<PingDatabaseService>(qualifier)).toProvider {
        PingDatabaseService(config, deploymentProvider.get())
      }.asSingleton()
      install(ServiceModule<PingDatabaseService>(qualifier))
    }

    // Bind DataSourceService.
    val dataSourceDecoratorsProvider = getProvider(dataSourceDecoratorsKey)
    bind(keyOf<DataSource>(qualifier)).toProvider(keyOf<DataSourceService>(qualifier))
    bind(keyOf<DataSourceService>(qualifier)).toProvider(object : Provider<DataSourceService> {
      @com.google.inject.Inject(optional = true) var registry: CollectorRegistry? = null
      override fun get(): DataSourceService {
        return DataSourceService(
          qualifier = qualifier,
          baseConfig = config,
          deployment = deploymentProvider.get(),
          dataSourceDecorators = dataSourceDecoratorsProvider.get(),
          databasePool = databasePool,
          collectorRegistry = registry,
        )
      }
    }).asSingleton()
    val dataSourceServiceProvider = getProvider(keyOf<DataSourceService>(qualifier))

    bind(keyOf<DataSourceConnector>(qualifier)).toProvider(dataSourceServiceProvider)
    install(
      ServiceModule<DataSourceService>(qualifier)
        .let {
          if (installHealthCheck) it.dependsOn<PingDatabaseService>(qualifier)
          else it
        }
        .enhancedBy<ReadyService>()
    )
    bind(keyOf<Transacter>(qualifier)).toProvider { RealTransacter(dataSourceServiceProvider.get()) }

    if (config.type == DataSourceType.VITESS_MYSQL) {
      val spanInjectorDecoratorKey = SpanInjector::class.toKey(qualifier)
      bind(spanInjectorDecoratorKey)
        .toProvider(object : Provider<SpanInjector> {
          @com.google.inject.Inject(optional = true) var tracer: Tracer? = null

          override fun get(): SpanInjector =
            SpanInjector(tracer, config)
        }).asSingleton()
    }

    if (config.type == DataSourceType.MYSQL && config.mysql_use_aws_secret_for_credentials) {
      val tracingDecoratorKey = TracingDataSourceDecorator::class.toKey(qualifier)
      bind(tracingDecoratorKey)
        .toProvider(object : Provider<TracingDataSourceDecorator> {
          @com.google.inject.Inject(optional = true) var tracer: Tracer? = null

          override fun get(): TracingDataSourceDecorator =
            TracingDataSourceDecorator(tracer, config)
        }).asSingleton()
    }
  }
}
