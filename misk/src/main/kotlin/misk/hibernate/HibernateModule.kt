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
 * Binds database connectivity for a qualified datasource. This binds the following public types,
 * both annotated with the qualifier:
 *
 *  * [DataSourceConfig]
 *  * [SessionFactory]
 *  * [Service], in a multibinder, that connects at start up and disconnects at shut down.
 *
 * This also binds internal types annotated with the qualifier:
 *
 *  * [SchemaMigrator]
 *  * [HibernateConnector]
 */
class HibernateModule(
  private val qualifier: KClass<out Annotation>,
  private val config: DataSourceConfig
) : KAbstractModule() {
  override fun configure() {
    val configKey = DataSourceConfig::class.toKey(qualifier)

    val entitiesKey = setOfType(HibernateEntity::class).toKey(qualifier)
    val entitiesProvider = getProvider(entitiesKey)

    val sessionFactoryKey = SessionFactory::class.toKey(qualifier)
    val sessionFactoryProvider = getProvider(sessionFactoryKey)

    val connectorKey = HibernateConnector::class.toKey(qualifier)
    val connectorProvider = getProvider(connectorKey)

    val schemaMigratorKey = SchemaMigrator::class.toKey(qualifier)
    val schemaMigratorProvider = getProvider(schemaMigratorKey)

    bind(configKey).toInstance(config)

    bind(sessionFactoryKey).toProvider(connectorKey).asSingleton()

    bind(connectorKey).toProvider(Provider<HibernateConnector> {
      HibernateConnector(qualifier, config, entitiesProvider.get())
    }).asSingleton()

    bind(schemaMigratorKey).toProvider(object : Provider<SchemaMigrator> {
      @Inject lateinit var resourceLoader: ResourceLoader
      override fun get(): SchemaMigrator = SchemaMigrator(resourceLoader,
          sessionFactoryProvider.get(), config)
    }).asSingleton()

    binder().addMultibinderBinding<Service>().toProvider(object : Provider<HibernateService> {
      @Inject lateinit var environment: Environment
      override fun get(): HibernateService = HibernateService(environment, qualifier,
          connectorProvider.get(), schemaMigratorProvider)
    }).asSingleton()
  }
}
