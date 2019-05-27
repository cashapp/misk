package misk.hibernate

import io.opentracing.Tracer
import misk.ServiceModule
import misk.environment.Environment
import misk.hibernate.ReflectionQuery.QueryLimitsConfig
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.inject.setOfType
import misk.inject.toKey
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceDecorator
import misk.jdbc.DataSourceService
import misk.jdbc.PingDatabaseService
import misk.metrics.Metrics
import misk.resources.ResourceLoader
import misk.vitess.StartVitessService
import org.hibernate.SessionFactory
import org.hibernate.event.spi.EventType
import javax.inject.Inject
import javax.inject.Provider
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
  config: DataSourceConfig
) : KAbstractModule() {
  val config = config.withDefaults()

  override fun configure() {
    val sessionFactoryProvider = getProvider(keyOf<SessionFactory>(qualifier))
    val environmentProvider: Provider<Environment> = getProvider(keyOf<Environment>())

    newMultibinder<DataSourceDecorator>(qualifier)

    bind<Query.Factory>().to<ReflectionQuery.Factory>()
    bind<QueryLimitsConfig>()
        .toInstance(QueryLimitsConfig(MAX_MAX_ROWS, ROW_COUNT_ERROR_LIMIT, ROW_COUNT_WARNING_LIMIT))
    bind(keyOf<DataSourceConfig>(qualifier)).toInstance(config)

    // Bind StartVitessService.
    install(ServiceModule<StartVitessService>(qualifier))
    bind(keyOf<StartVitessService>(qualifier)).toProvider(object : Provider<StartVitessService> {
      @Inject lateinit var environment: Environment
      override fun get(): StartVitessService {
        return StartVitessService(environment = environment, config = config, qualifier = qualifier)
      }
    }).asSingleton()

    // Bind PingDataBaseService.
    bind(keyOf<PingDatabaseService>(qualifier)).toProvider(Provider<PingDatabaseService> {
      PingDatabaseService(config, environmentProvider.get())
    }).asSingleton()
    // TODO(rhall): depending on Vitess is a hack to simulate Vitess has already been started in the
    // env. This is to remove flakiness in tests that are not waiting until Vitess is ready.
    // This should be replaced with an ExternalDependency that manages vitess.
    install(ServiceModule<PingDatabaseService>(qualifier)
        .dependsOn<StartVitessService>(qualifier))

    // Bind DataSourceService.
    val dataSourceDecoratorsKey = setOfType(DataSourceDecorator::class).toKey(qualifier)
    val dataSourceDecoratorsProvider = getProvider(dataSourceDecoratorsKey)
    bind(keyOf<DataSource>(qualifier)).toProvider(keyOf<DataSourceService>(qualifier)).asSingleton()
    bind(keyOf<DataSourceService>(qualifier)).toProvider(object : Provider<DataSourceService> {
      @com.google.inject.Inject(optional = true) var metrics: Metrics? = null
      override fun get() = DataSourceService(
          qualifier,
          config,
          environmentProvider.get(),
          dataSourceDecoratorsProvider.get(),
          metrics
      )
    }).asSingleton()
    install(ServiceModule<DataSourceService>(qualifier)
        .dependsOn<PingDatabaseService>(qualifier))

    // Bind SessionFactoryService.
    val entitiesKey = setOfType(HibernateEntity::class).toKey(qualifier)
    val entitiesProvider = getProvider(entitiesKey)
    val eventListenersKey = setOfType(ListenerRegistration::class).toKey(qualifier)
    val eventListenersProvider = getProvider(eventListenersKey)
    val hibernateInjectorAccessProvider = getProvider(HibernateInjectorAccess::class.java)
    val dataSourceProvider = getProvider(keyOf<DataSource>(qualifier))

    bind(keyOf<SessionFactory>(qualifier))
        .toProvider(keyOf<SessionFactoryService>(qualifier))
        .asSingleton()
    bind(keyOf<SessionFactoryService>(qualifier)).toProvider(Provider<SessionFactoryService> {
      SessionFactoryService(qualifier, config, dataSourceProvider,
          hibernateInjectorAccessProvider.get(),
          entitiesProvider.get(), eventListenersProvider.get())
    }).asSingleton()
    install(ServiceModule<SessionFactoryService>(qualifier)
        .dependsOn<DataSourceService>(qualifier))

    // Bind SchemaMigratorService.
    val transacterKey = Transacter::class.toKey(qualifier)
    val transacterProvider = getProvider(transacterKey)
    val schemaMigratorKey = SchemaMigrator::class.toKey(qualifier)
    val schemaMigratorProvider = getProvider(schemaMigratorKey)

    bind(schemaMigratorKey).toProvider(object : Provider<SchemaMigrator> {
      @Inject lateinit var resourceLoader: ResourceLoader
      override fun get(): SchemaMigrator = SchemaMigrator(qualifier, resourceLoader,
          transacterProvider, config)
    }).asSingleton()
    bind(transacterKey).toProvider(object : Provider<Transacter> {
      @com.google.inject.Inject(optional = true) val tracer: Tracer? = null
      @Inject lateinit var queryTracingListener: QueryTracingListener
      override fun get(): RealTransacter = RealTransacter(
          qualifier,
          sessionFactoryProvider,
          config,
          queryTracingListener,
          tracer
      )
    }).asSingleton()

    bind(keyOf<SchemaMigratorService>(
        qualifier)).toProvider(object : Provider<SchemaMigratorService> {
      @Inject lateinit var environment: Environment
      override fun get(): SchemaMigratorService = SchemaMigratorService(
          environment,
          schemaMigratorProvider,
          config
      )
    }).asSingleton()
    install(ServiceModule<SchemaMigratorService>(qualifier)
        .dependsOn<SessionFactoryService>(qualifier))

    // Bind SchemaValidatorService.
    val sessionFactoryServiceProvider = getProvider(keyOf<SessionFactoryService>(qualifier))
    bind(keyOf<SchemaValidatorService>(qualifier))
        .toProvider(Provider<SchemaValidatorService> {
          SchemaValidatorService(
              qualifier,
              sessionFactoryServiceProvider,
              transacterProvider,
              config
          )
        }).asSingleton()
    install(ServiceModule<SchemaValidatorService>(qualifier)
        .dependsOn<SchemaMigratorService>(qualifier)
        .dependsOn<SessionFactoryService>(qualifier))

    // Install other modules.
    install(object : HibernateEntityModule(qualifier) {
      override fun configureHibernate() {
        bindListener(EventType.PRE_INSERT).to<TimestampListener>()
        bindListener(EventType.PRE_UPDATE).to<TimestampListener>()
        bindListener(EventType.PRE_INSERT).to<QueryTracingListener>()
        bindListener(EventType.POST_INSERT).to<QueryTracingListener>()
        bindListener(EventType.PRE_UPDATE).to<QueryTracingListener>()
        bindListener(EventType.POST_UPDATE).to<QueryTracingListener>()
        bindListener(EventType.PRE_DELETE).to<QueryTracingListener>()
        bindListener(EventType.POST_DELETE).to<QueryTracingListener>()
      }
    })

    install(HibernateHealthCheckModule(qualifier, sessionFactoryProvider))
  }
}
