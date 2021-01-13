package misk.hibernate

import com.google.inject.Injector
import misk.ServiceModule
import misk.concurrent.ExecutorServiceFactory
import misk.healthchecks.HealthCheck
import misk.hibernate.ReflectionQuery.QueryLimitsConfig
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.inject.setOfType
import misk.inject.toKey
import misk.inject.typeLiteral
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceDecorator
import misk.jdbc.DataSourceService
import misk.jdbc.DataSourceType
import misk.jdbc.DatabasePool
import misk.jdbc.JdbcModule
import misk.jdbc.RealDatabasePool
import misk.jdbc.SchemaMigratorService
import misk.web.exceptions.ExceptionMapperModule
import org.hibernate.SessionFactory
import org.hibernate.event.spi.EventType
import org.hibernate.exception.ConstraintViolationException
import java.time.Clock
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
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

    install(JdbcModule(qualifier, config, readerQualifier, readerConfig, databasePool))

    bind<Query.Factory>().to<ReflectionQuery.Factory>()
    bind<QueryLimitsConfig>()
        .toInstance(QueryLimitsConfig(MAX_MAX_ROWS, ROW_COUNT_ERROR_LIMIT, ROW_COUNT_WARNING_LIMIT))

    bindDataSource(qualifier, config, true)
    if (readerQualifier != null && readerConfig != null) {
      bindDataSource(readerQualifier, readerConfig, false)
    }

    newMultibinder<DataSourceDecorator>(qualifier)

    val transacterKey = Transacter::class.toKey(qualifier)
    val sessionFactoryProvider = getProvider(keyOf<SessionFactory>(qualifier))
    val readerSessionFactoryProvider =
        if (readerQualifier != null) getProvider(keyOf<SessionFactory>(readerQualifier)) else null

    install(ServiceModule<SchemaMigratorService>(qualifier)
        .dependsOn<DataSourceService>(qualifier))

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

    /**
     * Reader transacter is only supported for MySQL for now. TiDB and Vitess replica read works
     * a bit differently than MySQL.
     */
    if (readerQualifier != null && config.type == DataSourceType.MYSQL) {
      val readerTransacterKey = Transacter::class.toKey(readerQualifier)
      bind(readerTransacterKey).toProvider(object : Provider<Transacter> {
        @Inject lateinit var executorServiceFactory: ExecutorServiceFactory
        @Inject lateinit var injector: Injector
        override fun get(): Transacter = RealTransacter(
            qualifier = readerQualifier,
            sessionFactoryProvider = readerSessionFactoryProvider!!,
            readerSessionFactoryProvider = readerSessionFactoryProvider,
            config = config,
            executorServiceFactory = executorServiceFactory,
            hibernateEntities = injector.findBindingsByType(HibernateEntity::class.typeLiteral()).map {
              it.provider.get()
            }.toSet()
        ).readOnly()
      }).asSingleton()
    }

    // Install other modules.
    install(object : HibernateEntityModule(qualifier) {
      override fun configureHibernate() {
        bindListener(EventType.PRE_INSERT).to<TimestampListener>()
        bindListener(EventType.PRE_UPDATE).to<TimestampListener>()
      }
    })

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
    val eventListenersProvider =
        getProvider(setOfType(ListenerRegistration::class).toKey(this.qualifier))

    val sessionFactoryProvider = getProvider(keyOf<SessionFactory>(qualifier))

    val sessionFactoryServiceProvider = getProvider(keyOf<SessionFactoryService>(qualifier))

    // Bind SessionFactoryService as implementation of TransacterService.
    val hibernateInjectorAccessProvider = getProvider(HibernateInjectorAccess::class.java)
    val dataSourceProvider = getProvider(keyOf<DataSource>(qualifier))
    val dataSourceServiceProvider = getProvider(keyOf<DataSourceService>(qualifier))

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
