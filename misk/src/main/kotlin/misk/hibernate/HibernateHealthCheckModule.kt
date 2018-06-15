package misk.hibernate

import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.inject.asSingleton
import misk.jdbc.DataSourceConfig
import org.hibernate.SessionFactory
import java.time.Clock
import javax.inject.Inject
import javax.inject.Provider

/**
 * Binds a HealthCheck for Hibernate.
 */
class HibernateHealthCheckModule(
  private val sessionFactoryProvider: Provider<SessionFactory>,
  private val config: DataSourceConfig
) : KAbstractModule() {

  override fun configure() {
    binder().addMultibinderBinding<HealthCheck>()
      .toProvider(object : Provider<HibernateHealthCheck> {
        @Inject lateinit var clock: Clock

        override fun get(): HibernateHealthCheck =
          HibernateHealthCheck(sessionFactoryProvider.get(), config, clock)
      }).asSingleton()
  }
}
