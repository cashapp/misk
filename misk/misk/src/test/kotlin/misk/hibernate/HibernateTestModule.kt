package misk.hibernate

import misk.MiskModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceClustersConfig
import misk.jdbc.DataSourceConfig
import misk.resources.ResourceLoaderModule

/** This module supports our Hibernate tests. */
class HibernateTestModule : KAbstractModule() {
  override fun configure() {
    bind(Environment::class.java).toInstance(Environment.TESTING)
    install(ResourceLoaderModule())
    install(MiskModule())

    val rootConfig = MiskConfig.load<RootConfig>("test_hibernate_app", Environment.TESTING)
    val config: DataSourceConfig = rootConfig.data_source_clusters["exemplar"]!!.writer
    install(HibernateTestingModule(Movies::class))
    install(HibernateModule(Movies::class, config))
    install(object : HibernateEntityModule(Movies::class) {
      override fun configureHibernate() {
        addEntities(DbMovie::class, DbActor::class, DbCharacter::class)
      }
    })
  }

  data class RootConfig(val data_source_clusters: DataSourceClustersConfig) : Config
}