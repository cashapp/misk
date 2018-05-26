package misk.hibernate

import com.google.common.util.concurrent.Service
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.inject.asSingleton
import misk.inject.setOfType
import misk.inject.toKey
import misk.jdbc.DataSourceConfig
import org.hibernate.SessionFactory
import javax.inject.Provider
import kotlin.reflect.KClass

/**
 * Binds a SessionFactory for a qualified datasource.
 */
class HibernateModule(
  private val qualifier: KClass<out Annotation>,
  private val config: DataSourceConfig
) : KAbstractModule() {
  override fun configure() {
    bind<DataSourceConfig>().annotatedWith(qualifier.java).toInstance(config)

    val entitiesKey = setOfType(HibernateEntity::class).toKey(qualifier)
    val serviceKey = HibernateService::class.toKey(qualifier)
    val entitiesProvider = getProvider(entitiesKey)
    val serviceProvider = getProvider(serviceKey)

    bind(serviceKey).toProvider(Provider<HibernateService> {
      HibernateService(qualifier, config, entitiesProvider.get())
    }).asSingleton()

    binder().addMultibinderBinding<Service>().to(serviceKey)

    bind(SessionFactory::class.java)
        .annotatedWith(qualifier.java)
        .toProvider(Provider<SessionFactory> { serviceProvider.get().sessionFactory })
        .asSingleton()
  }
}
