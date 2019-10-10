package misk.hibernate

import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.logging.LogCollectorModule
import misk.testing.MockTracingBackendModule
import misk.time.FakeClockModule

/** This module creates movies, actors, and characters tables for several Hibernate tests. */
class MoviesTestModule(
  private val type: DataSourceType = DataSourceType.VITESS_MYSQL
) : KAbstractModule() {
  override fun configure() {
    install(LogCollectorModule())
    install(
        Modules.override(MiskTestingServiceModule()).with(FakeClockModule(),
            MockTracingBackendModule()))
    install(EnvironmentModule(Environment.TESTING))

    val config = MiskConfig.load<MoviesConfig>("moviestestmodule", Environment.TESTING)
    install(HibernateTestingModule(Movies::class))
    val dataSourceConfig = selectDataSourceConfig(config)
    install(HibernateModule(Movies::class, MoviesReader::class,
        DataSourceClusterConfig(writer = dataSourceConfig, reader = dataSourceConfig)))
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
