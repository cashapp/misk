package misk.hibernate

import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.logging.LogCollectorModule
import misk.testing.MockTracingBackendModule
import misk.time.FakeClockModule

/** This module creates movies, actors, and characters tables for several Hibernate tests. */
class MoviesTestModule(
  /**
   * Disable the cross shard query detector. This is a temporary workaround for too many failing
   * tests. This should eventually be removed.
   */
  val disableCrossShardQueryDetector: Boolean = false,
  private val useVitess: Boolean = true
) : KAbstractModule() {
  override fun configure() {
    install(LogCollectorModule())
    install(
        Modules.override(MiskTestingServiceModule()).with(FakeClockModule(), MockTracingBackendModule()))
    install(EnvironmentModule(Environment.TESTING))

    val config = MiskConfig.load<MoviesConfig>("moviestestmodule", Environment.TESTING)
    install(HibernateTestingModule(Movies::class,
        disableChecks = useVitess && disableCrossShardQueryDetector))
    install(HibernateModule(Movies::class, selectDataSourceConfig(config)))
    install(object : HibernateEntityModule(Movies::class) {
      override fun configureHibernate() {
        addEntities(DbMovie::class, DbActor::class, DbCharacter::class)
      }
    })
  }

  private fun selectDataSourceConfig(config: MoviesConfig) : DataSourceConfig {
    return if (useVitess)
      config.data_source
    else
      config.mysql_data_source
  }

  data class MoviesConfig(
      val data_source: DataSourceConfig,
      val mysql_data_source: DataSourceConfig) : Config
}
