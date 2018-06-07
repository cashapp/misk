package misk.hibernate

import misk.MiskModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.resources.ResourceLoaderModule

/** This module creates movies, actors, and characters tables for several Hibernate tests. */
class MoviesTestModule : KAbstractModule() {
  override fun configure() {
    install(ResourceLoaderModule())
    install(MiskModule())
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