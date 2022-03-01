package misk.jdbc

import io.opentracing.Tracer
import io.prometheus.client.CollectorRegistry
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
import javax.inject.Inject
import javax.inject.Provider
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
class JdbcModule(
  private val qualifier: KClass<out Annotation>,
  config: DataSourceConfig,
  private val readerQualifier: KClass<out Annotation>?,
  readerConfig: DataSourceConfig?,
  val databasePool: DatabasePool = RealDatabasePool
) : KAbstractModule() {
  val config = config.withDefaults()
  val readerConfig = readerConfig?.withDefaults()

  constructor(
    qualifier: KClass<out Annotation>,
    config: DataSourceConfig,
    databasePool: DatabasePool = RealDatabasePool
  ) : this(qualifier, config, null, null, databasePool)

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
      @Inject lateinit var deployment: Deployment
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

    val schemaMigratorKey = SchemaMigrator::class.toKey(qualifier)
    val schemaMigratorProvider = getProvider(schemaMigratorKey)
    val connectorProvider = getProvider(keyOf<DataSourceConnector>(qualifier))

    val dataSourceServiceProvider = getProvider(keyOf<DataSourceService>(qualifier))
    bind(schemaMigratorKey).toProvider(object : Provider<SchemaMigrator> {
      @Inject lateinit var resourceLoader: ResourceLoader
      override fun get(): SchemaMigrator = SchemaMigrator(
        qualifier = qualifier,
        resourceLoader = resourceLoader,
        dataSourceConfig = config,
        dataSource = dataSourceServiceProvider.get(),
        connector = connectorProvider.get()
      )
    }).asSingleton()

    val schemaMigratorServiceKey = keyOf<SchemaMigratorService>(qualifier)
    bind(schemaMigratorServiceKey)
      .toProvider(object : Provider<SchemaMigratorService> {
        @Inject lateinit var deployment: Deployment
        override fun get(): SchemaMigratorService = SchemaMigratorService(
          qualifier = qualifier,
          deployment = deployment,
          schemaMigratorProvider = schemaMigratorProvider,
          connectorProvider = connectorProvider
        )
      }).asSingleton()

    install(
      ServiceModule<SchemaMigratorService>(qualifier)
        .dependsOn<DataSourceService>(qualifier)
    )

    multibind<HealthCheck>().to(schemaMigratorServiceKey)
  }

  private fun bindDataSource(
    qualifier: KClass<out Annotation>,
    config: DataSourceConfig,
    isWriter: Boolean
  ) {

    // These items are configured on the writer qualifier only
    val dataSourceDecoratorsKey = setOfType(DataSourceDecorator::class).toKey(this.qualifier)

    val deploymentProvider: Provider<Deployment> = getProvider(keyOf<Deployment>())

    bind(keyOf<DataSourceConfig>(qualifier)).toInstance(config)

    // Bind PingDatabaseService.
    bind(keyOf<PingDatabaseService>(qualifier)).toProvider(Provider {
      PingDatabaseService(config, deploymentProvider.get())
    }).asSingleton()
    // TODO(rhall): depending on Vitess is a hack to simulate Vitess has already been started in the
    // env. This is to remove flakiness in tests that are not waiting until Vitess is ready.
    // This should be replaced with an ExternalDependency that manages vitess.
    // TODO(jontirsen): I don't think this is needed anymore...
    install(
      ServiceModule<PingDatabaseService>(qualifier)
        .dependsOn<StartDatabaseService>(this.qualifier)
    )

    // Bind DataSourceService.
    val dataSourceDecoratorsProvider = getProvider(dataSourceDecoratorsKey)
    bind(keyOf<DataSource>(qualifier))
      .toProvider(keyOf<DataSourceService>(qualifier))
      .asSingleton()
    bind(keyOf<DataSourceService>(qualifier)).toProvider(object : Provider<DataSourceService> {
      @com.google.inject.Inject(optional = true) var registry: CollectorRegistry? = null
      override fun get(): DataSourceService {
        return DataSourceService(
          qualifier = qualifier,
          baseConfig = config,
          deployment = deploymentProvider.get(),
          dataSourceDecorators = dataSourceDecoratorsProvider.get(),
          databasePool = databasePool,
          // TODO provide metrics to the reader pool but need a different metric key prefix
          collectorRegistry = if (isWriter) registry else null
        )
      }
    }).asSingleton()
    val dataSourceServiceProvider = getProvider(keyOf<DataSourceService>(qualifier))
    val dataSourceProvider = getProvider(keyOf<DataSource>(qualifier))

    bind(keyOf<DataSourceConnector>(qualifier)).toProvider(dataSourceServiceProvider)
    install(
      ServiceModule<DataSourceService>(qualifier)
        .dependsOn<PingDatabaseService>(qualifier)
    )
    bind(keyOf<Transacter>(qualifier))
      .toProvider(Provider<Transacter> { RealTransacter(dataSourceProvider.get()) })

    if (config.type == DataSourceType.VITESS_MYSQL) {
      val spanInjectorDecoratorKey = SpanInjector::class.toKey(qualifier)
      bind(spanInjectorDecoratorKey)
        .toProvider(object : Provider<SpanInjector> {
          @com.google.inject.Inject(optional = true)
          var tracer: Tracer? = null

          override fun get(): SpanInjector =
            SpanInjector(tracer, config)
        }).asSingleton()
    }
  }
}
