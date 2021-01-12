package misk.hibernate

import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.logging.LogCollectorModule
import misk.testing.MockTracingBackendModule
import misk.time.FakeClockModule

/** This module creates movies, actors, and characters tables for several Hibernate tests. */
class MoviesTestModule(
  private val type: DataSourceType = DataSourceType.VITESS_MYSQL,
  private val scaleSafetyChecks: Boolean = false,
  private val entitiesModule: HibernateEntityModule = object :
    HibernateEntityModule(Movies::class) {
    override fun configureHibernate() {
      addEntities(DbMovie::class, DbActor::class, DbCharacter::class)
    }
  }
) : KAbstractModule() {
  override fun configure() {
    install(LogCollectorModule())
    install(
      Modules.override(MiskTestingServiceModule()).with(
        FakeClockModule(),
        MockTracingBackendModule()
      )
    )
    install(DeploymentModule.forTesting())

    val config = MiskConfig.load<MoviesConfig>("moviestestmodule", Environment.TESTING)
    val dataSourceConfig = selectDataSourceConfig(config)
    install(
      HibernateTestingModule(
        Movies::class,
        dataSourceConfig,
        scaleSafetyChecks = scaleSafetyChecks
      )
    )
    install(
      HibernateModule(
        Movies::class, MoviesReader::class,
        DataSourceClusterConfig(writer = dataSourceConfig, reader = dataSourceConfig)
      )
    )
    install(entitiesModule)
  }

  private fun selectDataSourceConfig(config: MoviesConfig): DataSourceConfig {
    return when (type) {
      DataSourceType.VITESS_MYSQL -> config.vitess_mysql_data_source
      DataSourceType.MYSQL -> config.mysql_data_source
      DataSourceType.COCKROACHDB -> config.cockroachdb_data_source
      DataSourceType.POSTGRESQL -> config.postgresql_data_source
      DataSourceType.TIDB -> config.tidb_data_source
      DataSourceType.HSQLDB -> throw RuntimeException("Not supported (yet?)")
    }
  }
}
