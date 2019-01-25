package misk.hibernate

import misk.jdbc.DataSourceConfig
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import org.hibernate.SessionFactory
import java.time.Clock
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

/**
 * Binds a HealthCheck for Hibernate.
 */
class HibernateHealthCheckModule(
  private val qualifier: KClass<out Annotation>,
  private val sessionFactoryProvider: Provider<SessionFactory>,
  private val config: DataSourceConfig
) : KAbstractModule() {

  override fun configure() {
    multibind<HealthCheck>()
      .toProvider(object : Provider<HibernateHealthCheck> {
        @Inject lateinit var clock: Clock

        override fun get(): HibernateHealthCheck =
          HibernateHealthCheck(qualifier, sessionFactoryProvider, config, clock)
      }).asSingleton()
  }
}
