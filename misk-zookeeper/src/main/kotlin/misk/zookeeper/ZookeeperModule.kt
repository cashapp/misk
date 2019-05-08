package misk.zookeeper

import misk.clustering.zookeeper.ZookeeperConfig
import misk.inject.KAbstractModule
import kotlin.reflect.KClass

class ZookeeperModule(
  private val config: ZookeeperConfig,
  private val qualifier: KClass<out Annotation>? = null
) : KAbstractModule() {
  override fun configure() {
    install(CuratorFrameworkModule(config, qualifier))
    install(FixedEnsembleProviderModule(config, qualifier))
  }
}