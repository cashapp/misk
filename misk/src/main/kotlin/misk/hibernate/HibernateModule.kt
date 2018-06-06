package misk.hibernate

import com.google.common.util.concurrent.Service
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.inject.asSingleton
import misk.inject.setOfType
import misk.inject.toKey
import misk.jdbc.DataSourceConfig
import misk.resources.ResourceLoader
import org.hibernate.SessionFactory
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

    val eventListenersKey = setOfType(HibernateEventListener::class).toKey(qualifier)
    val eventListenersProvider = getProvider(eventListenersKey)

    val sessionFactoryKey = SessionFactory::class.toKey(qualifier)
    val sessionFactoryProvider = getProvider(sessionFactoryKey)

    val sessionFactoryServiceKey = SessionFactoryService::class.toKey(qualifier)

    val schemaMigratorKey = SchemaMigrator::class.toKey(qualifier)
    val schemaMigratorProvider = getProvider(schemaMigratorKey)

    val transacterKey = Transacter::class.toKey(qualifier)

    bind(configKey).toInstance(config)

    bind(sessionFactoryKey).toProvider(sessionFactoryServiceKey).asSingleton()
    bind(sessionFactoryServiceKey).toProvider(Provider<SessionFactoryService> {
      SessionFactoryService(qualifier, config, entitiesProvider.get(), eventListenersProvider.get())
    }).asSingleton()
    binder().addMultibinderBinding<Service>().to(sessionFactoryServiceKey)

    bind(schemaMigratorKey).toProvider(object : Provider<SchemaMigrator> {
      @Inject lateinit var resourceLoader: ResourceLoader
      override fun get(): SchemaMigrator = SchemaMigrator(resourceLoader,
          sessionFactoryProvider.get(), config)
    }).asSingleton()

    bind(transacterKey).toProvider(Provider<Transacter> {
      RealTransacter(sessionFactoryProvider.get())
    }).asSingleton()

    binder().addMultibinderBinding<Service>().toProvider(object : Provider<SchemaMigratorService> {
      @Inject lateinit var environment: Environment
      override fun get(): SchemaMigratorService = SchemaMigratorService(
          environment, qualifier, schemaMigratorProvider)
    }).asSingleton()

    bind(Query.Factory::class.java).to(ReflectionQuery.Factory::class.java)
  }
}
