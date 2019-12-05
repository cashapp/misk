package misk.vitess

import misk.jdbc.DataSourceConfig
import kotlin.reflect.KClass

class StartVitessService {
  companion object {
    @Deprecated("Renamed", replaceWith = ReplaceWith("DockerVitessCluster.startVitessDaemon(qualifier,config)"))
    fun startVitessDaemon(
      /** The same qualifier passed into [HibernateModule], used to uniquely name the container */
      qualifier: KClass<out Annotation>,
      /** Config for the Vitess cluster */
      config: DataSourceConfig
    ) {
      startVitessDaemon(qualifier, config)
    }
  }
}