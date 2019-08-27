package misk.hibernate

import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.logging.LogCollectorModule
import misk.testing.MockTracingBackendModule
import misk.time.FakeClockModule

/** This module creates movies, actors, and characters tables for several Hibernate tests. */
class MoviesTestModule(
  // TODO(jontirsen): Make this VITESS_MYSQL when this is merged: https://github.com/vitessio/vitess/pull/5136
  private val type: DataSourceType = DataSourceType.VITESS
) : KAbstractModule() {
  override fun configure() {
    install(LogCollectorModule())
    install(
        Modules.override(MiskTestingServiceModule()).with(FakeClockModule(), MockTracingBackendModule()))
    install(EnvironmentModule(Environment.TESTING))

    val config = MiskConfig.load<MoviesConfig>("moviestestmodule", Environment.TESTING)
    install(HibernateTestingModule(Movies::class))
    install(HibernateModule(Movies::class, selectDataSourceConfig(config)))
    install(object : HibernateEntityModule(Movies::class) {
      override fun configureHibernate() {
        addEntities(DbMovie::class, DbActor::class, DbCharacter::class)
      }
    })
  }

  private fun selectDataSourceConfig(config: MoviesConfig): DataSourceConfig {
    return when (type) {
      DataSourceType.VITESS -> config.vitess_data_source
      DataSourceType.VITESS_MYSQL -> config.vitess_mysql_data_source
      DataSourceType.MYSQL -> config.mysql_data_source
      DataSourceType.HSQLDB -> throw RuntimeException("Not supported (yet?)")
    }
  }

  data class MoviesConfig(
    val vitess_data_source: DataSourceConfig,
    val vitess_mysql_data_source: DataSourceConfig,
    val mysql_data_source: DataSourceConfig
  ) : Config
}
