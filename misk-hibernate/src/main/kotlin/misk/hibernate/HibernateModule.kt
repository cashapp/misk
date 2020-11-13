package misk.hibernate

import com.google.inject.Injector
import io.opentracing.Tracer
import io.prometheus.client.CollectorRegistry
import misk.ServiceModule
import misk.concurrent.ExecutorServiceFactory
import misk.environment.Environment
import misk.healthchecks.HealthCheck
import misk.hibernate.ReflectionQuery.QueryLimitsConfig
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.inject.setOfType
import misk.inject.toKey
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceConnector
import misk.jdbc.DataSourceDecorator
import misk.jdbc.DataSourceService
import misk.jdbc.DataSourceType
import misk.jdbc.DatabasePool
import misk.jdbc.PingDatabaseService
import misk.jdbc.RealDatabasePool
import misk.jdbc.SpanInjector
import misk.metrics.Metrics
import misk.resources.ResourceLoader
import misk.database.StartDatabaseService
import misk.inject.typeLiteral
import misk.web.exceptions.ExceptionMapperModule
import org.hibernate.SessionFactory
import org.hibernate.event.spi.EventType
import org.hibernate.exception.ConstraintViolationException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Provider
import javax.persistence.OptimisticLockException
import javax.sql.DataSource
import kotlin.reflect.KClass

/**
 * Binds database connectivity for a qualified data source. This binds the following public types:
 *
 *  * @Qualifier [misk.jdbc.DataSourceConfig]
 *  * @Qualifier [SessionFactory]
 *  * @Qualifier [Transacter]
 *  * [Query.Factory] (with no qualifier)
 *
 * This also registers services to connect to the database ([SessionFactoryService]) and to verify
 * that the schema is up-to-date ([SchemaMigratorService]).
 */

private const val MAX_MAX_ROWS = 10_000
private const val ROW_COUNT_ERROR_LIMIT = 3000
private const val ROW_COUNT_WARNING_LIMIT = 2000

class HibernateModule(
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

  constructor(
    qualifier: KClass<out Annotation>,
    readerQualifier: KClass<out Annotation>,
    cluster: DataSourceClusterConfig,
    databasePool: DatabasePool = RealDatabasePool
  ) : this(qualifier, cluster.writer, readerQualifier, cluster.reader, databasePool)

  override fun configure() {
    if (readerQualifier != null) {
      check(readerConfig != null) {
        "Reader not configured for datasource $readerQualifier"
      }
    }

    bind<Query.Factory>().to<ReflectionQuery.Factory>()
    bind<QueryLimitsConfig>()
        .toInstance(QueryLimitsConfig(MAX_MAX_ROWS, ROW_COUNT_ERROR_LIMIT, ROW_COUNT_WARNING_LIMIT))

    // Bind StartVitessService.
    install(ServiceModule<StartDatabaseService>(qualifier))
    bind(keyOf<StartDatabaseService>(qualifier)).toProvider(object : Provider<StartDatabaseService> {
      @Inject lateinit var environment: Environment
      override fun get(): StartDatabaseService {
        return StartDatabaseService(environment = environment, config = config, qualifier = qualifier)
      }
    }).asSingleton()

    bindDataSource(qualifier, config, true)
    if (readerQualifier != null && readerConfig != null) {
      bindDataSource(readerQualifier, readerConfig, false)
    }

    newMultibinder<DataSourceDecorator>(qualifier)

    val schemaMigratorKey = SchemaMigrator::class.toKey(qualifier)
    val schemaMigratorProvider = getProvider(schemaMigratorKey)
    val transacterKey = Transacter::class.toKey(qualifier)
    val transacterProvider = getProvider(transacterKey)
    val connectorProvider = getProvider(keyOf<DataSourceConnector>(qualifier))
    val sessionFactoryProvider = getProvider(keyOf<SessionFactory>(qualifier))
    val readerSessionFactoryProvider =
        if (readerQualifier != null) getProvider(keyOf<SessionFactory>(readerQualifier)) else null

    bind(schemaMigratorKey).toProvider(object : Provider<SchemaMigrator> {
      @Inject lateinit var resourceLoader: ResourceLoader
      override fun get(): SchemaMigrator = SchemaMigrator(
          qualifier = qualifier,
          resourceLoader = resourceLoader,
          transacter = transacterProvider,
          connector = connectorProvider.get()
      )
    }).asSingleton()

    val schemaMigratorServiceKey = keyOf<SchemaMigratorService>(qualifier)
    bind(schemaMigratorServiceKey)
        .toProvider(object : Provider<SchemaMigratorService> {
          @Inject lateinit var environment: Environment
          override fun get(): SchemaMigratorService = SchemaMigratorService(
              qualifier = qualifier,
              environment = environment,
              schemaMigratorProvider = schemaMigratorProvider,
              connectorProvider = connectorProvider
          )
        }).asSingleton()

    install(ServiceModule<SchemaMigratorService>(qualifier)
        .dependsOn<DataSourceService>(qualifier))

    multibind<HealthCheck>().to(schemaMigratorServiceKey)

    // Install other modules.
    install(object : HibernateEntityModule(qualifier) {
      override fun configureHibernate() {
        bindListener(EventType.PRE_INSERT).to<TimestampListener>()
        bindListener(EventType.PRE_UPDATE).to<TimestampListener>()
      }
    })

    bind(transacterKey).toProvider(object : Provider<Transacter> {
      @Inject lateinit var executorServiceFactory: ExecutorServiceFactory
      @Inject lateinit var injector: Injector
      override fun get(): RealTransacter = RealTransacter(
          qualifier = qualifier,
          sessionFactoryProvider = sessionFactoryProvider,
          readerSessionFactoryProvider = readerSessionFactoryProvider,
          config = config,
          executorServiceFactory = executorServiceFactory,
          hibernateEntities = injector.findBindingsByType(HibernateEntity::class.typeLiteral()).map {
            it.provider.get()
          }.toSet()
      )
    }).asSingleton()

    install(ExceptionMapperModule
        .create<RetryTransactionException, RetryTransactionExceptionMapper>())
    install(ExceptionMapperModule
        .create<ConstraintViolationException, ConstraintViolationExceptionMapper>())
    install(ExceptionMapperModule
        .create<OptimisticLockException, OptimisticLockExceptionMapper>())
  }

  private fun bindDataSource(
    qualifier: KClass<out Annotation>,
    config: DataSourceConfig,
    isWriter: Boolean
  ) {

    // These items are configured on the writer qualifier only
    val entitiesProvider = getProvider(setOfType(HibernateEntity::class).toKey(this.qualifier))
    val dataSourceDecoratorsKey = setOfType(DataSourceDecorator::class).toKey(this.qualifier)
    val eventListenersProvider = getProvider(setOfType(ListenerRegistration::class).toKey(this.qualifier))

    val environmentProvider: Provider<Environment> = getProvider(keyOf<Environment>())
    val sessionFactoryProvider = getProvider(keyOf<SessionFactory>(qualifier))

    bind(keyOf<DataSourceConfig>(qualifier)).toInstance(config)

    // Bind PingDatabaseService.
    bind(keyOf<PingDatabaseService>(qualifier)).toProvider(Provider {
      PingDatabaseService(config, environmentProvider.get())
    }).asSingleton()
    // TODO(rhall): depending on Vitess is a hack to simulate Vitess has already been started in the
    // env. This is to remove flakiness in tests that are not waiting until Vitess is ready.
    // This should be replaced with an ExternalDependency that manages vitess.
    // TODO(jontirsen): I don't think this is needed anymore...
    install(ServiceModule<PingDatabaseService>(qualifier)
        .dependsOn<StartDatabaseService>(this.qualifier))

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
            environment = environmentProvider.get(),
            dataSourceDecorators = dataSourceDecoratorsProvider.get(),
            databasePool = databasePool,
            // TODO provide metrics to the reader pool but need a different metric key prefix
            collectorRegistry = if (isWriter) registry else null
        )
      }
    }).asSingleton()
    val dataSourceServiceProvider = getProvider(keyOf<DataSourceService>(qualifier))
    bind(keyOf<DataSourceConnector>(qualifier)).toProvider(dataSourceServiceProvider)
    install(ServiceModule<DataSourceService>(qualifier)
        .dependsOn<PingDatabaseService>(qualifier))

    val sessionFactoryServiceProvider = getProvider(keyOf<SessionFactoryService>(qualifier))

    // Bind SessionFactoryService as implementation of TransacterService.
    val hibernateInjectorAccessProvider = getProvider(HibernateInjectorAccess::class.java)
    val dataSourceProvider = getProvider(keyOf<DataSource>(qualifier))

    bind(keyOf<SessionFactory>(qualifier))
        .toProvider(keyOf<SessionFactoryService>(qualifier))
        .asSingleton()
    bind(keyOf<TransacterService>(qualifier)).to(keyOf<SessionFactoryService>(qualifier))
    bind(keyOf<SessionFactoryService>(qualifier)).toProvider(Provider {
      SessionFactoryService(
          qualifier = qualifier,
          connector = dataSourceServiceProvider.get(),
          dataSource = dataSourceProvider,
          hibernateInjectorAccess = hibernateInjectorAccessProvider.get(),
          entityClasses = entitiesProvider.get(),
          listenerRegistrations = eventListenersProvider.get()
      )
    }).asSingleton()

    if (isWriter) {
      install(ServiceModule<TransacterService>(qualifier)
          .enhancedBy<SchemaMigratorService>(qualifier)
          .dependsOn<DataSourceService>(qualifier))
    } else {
      install(ServiceModule<TransacterService>(qualifier)
          .dependsOn<DataSourceService>(qualifier))
    }

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

    val healthCheckKey = keyOf<HealthCheck>(qualifier)
    bind(healthCheckKey)
        .toProvider(object : Provider<HibernateHealthCheck> {
          @Inject lateinit var clock: Clock

          override fun get() = HibernateHealthCheck(
              qualifier, sessionFactoryServiceProvider, sessionFactoryProvider, clock)
        })
        .asSingleton()
    multibind<HealthCheck>().to(healthCheckKey)
  }
}
