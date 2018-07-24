package misk.hibernate

import com.google.common.util.concurrent.Service
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.setOfType
import misk.inject.toKey
import misk.resources.ResourceLoader
import org.hibernate.SessionFactory
import org.hibernate.event.spi.EventType
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

/**
 * Binds database connectivity for a qualified datasource. This binds the following public types:
 *
 *  * @Qualifier [DataSourceConfig]
 *  * @Qualifier [SessionFactory]
 *  * @Qualifier [Transacter]
 *  * [Query.Factory] (with no qualifier)
 *
 * This also registers services to connect to the database ([SessionFactoryService]) and to verify
 * that the schema is up-to-date ([SchemaMigratorService]).
 */
class HibernateModule(
  private val qualifier: KClass<out Annotation>,
  private val config: DataSourceConfig
) : KAbstractModule() {
  override fun configure() {
    val configKey = DataSourceConfig::class.toKey(qualifier)

    val entitiesKey = setOfType(HibernateEntity::class).toKey(qualifier)
    val entitiesProvider = getProvider(entitiesKey)

    val environmentKey = Environment::class.toKey()
    val environmentProvider = getProvider(environmentKey)

    val eventListenersKey = setOfType(ListenerRegistration::class).toKey(qualifier)
    val eventListenersProvider = getProvider(eventListenersKey)

    val sessionFactoryKey = SessionFactory::class.toKey(qualifier)
    val sessionFactoryProvider = getProvider(sessionFactoryKey)

    val sessionFactoryServiceKey = SessionFactoryService::class.toKey(qualifier)

    val schemaMigratorKey = SchemaMigrator::class.toKey(qualifier)
    val schemaMigratorProvider = getProvider(schemaMigratorKey)

    val transacterKey = Transacter::class.toKey(qualifier)

    val pingDatabaseServiceKey = PingDatabaseService::class.toKey(qualifier)

    bind(configKey).toInstance(config)

    bind(pingDatabaseServiceKey).toProvider(Provider<PingDatabaseService> {
      PingDatabaseService(qualifier, config, environmentProvider.get())
    }).asSingleton()
    multibind<Service>().to(pingDatabaseServiceKey)

    bind(sessionFactoryKey).toProvider(sessionFactoryServiceKey).asSingleton()
    bind(sessionFactoryServiceKey).toProvider(Provider<SessionFactoryService> {
      SessionFactoryService(qualifier, config, environmentProvider.get(), entitiesProvider.get(),
          eventListenersProvider.get())
    }).asSingleton()
    multibind<Service>().to(sessionFactoryServiceKey)

    bind(schemaMigratorKey).toProvider(object : Provider<SchemaMigrator> {
      @Inject lateinit var resourceLoader: ResourceLoader
      override fun get(): SchemaMigrator = SchemaMigrator(qualifier, resourceLoader,
          sessionFactoryProvider.get(), config)
    }).asSingleton()

    bind(transacterKey).toProvider(Provider<Transacter> {
      RealTransacter(qualifier, sessionFactoryProvider.get(), config)
    }).asSingleton()

    multibind<Service>().toProvider(object : Provider<SchemaMigratorService> {
      @Inject lateinit var environment: Environment
      override fun get(): SchemaMigratorService = SchemaMigratorService(
          qualifier, environment, schemaMigratorProvider)
    }).asSingleton()

    bind<Query.Factory>().to<ReflectionQuery.Factory>()

    install(object : HibernateEntityModule(qualifier) {
      override fun configureHibernate() {
        bindListener(EventType.PRE_INSERT).to<TimestampListener>()
        bindListener(EventType.PRE_UPDATE).to<TimestampListener>()
      }
    })

    install(HibernateHealthCheckModule(qualifier, sessionFactoryProvider, config))
  }
}
