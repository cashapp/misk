package misk.hibernate

import com.google.inject.util.Modules
import misk.MiskModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.logging.LogCollectorModule
import misk.resources.ResourceLoaderModule
import misk.time.FakeClockModule

/** This module creates movies, actors, and characters tables for several Hibernate tests. */
class MoviesTestModule : KAbstractModule() {
  override fun configure() {
    install(LogCollectorModule())
    install(ResourceLoaderModule())
    install(Modules.override(MiskModule()).with(FakeClockModule()))
    install(EnvironmentModule(Environment.TESTING))

    val config = MiskConfig.load<MoviesConfig>("moviestestmodule", Environment.TESTING)
    install(HibernateTestingModule(Movies::class))
    install(HibernateModule(Movies::class, config.data_source))
    install(object : HibernateEntityModule(Movies::class) {
      override fun configureHibernate() {
        addEntities(DbMovie::class, DbActor::class, DbCharacter::class)
      }
    })
  }

  data class MoviesConfig(val data_source: DataSourceConfig) : Config
}