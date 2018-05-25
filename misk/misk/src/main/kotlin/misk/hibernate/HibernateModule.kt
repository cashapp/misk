package misk.hibernate

import com.google.common.util.concurrent.Service
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.jdbc.DataSourceConfig
import org.hibernate.SessionFactory
import javax.inject.Provider
import kotlin.reflect.KClass

class HibernateModule(
  private val qualifier: KClass<out Annotation>,
  private val config: DataSourceConfig,
  private val entityClasses: Set<KClass<*>>
) : KAbstractModule() {
  override fun configure() {
    val hibernateService = HibernateService(qualifier, config, entityClasses)
    binder().addMultibinderBinding<Service>().toInstance(hibernateService)
    bind(SessionFactory::class.java)
        .annotatedWith(qualifier.java)
        .toProvider(Provider<SessionFactory> { hibernateService.sessionFactory })
  }
}
