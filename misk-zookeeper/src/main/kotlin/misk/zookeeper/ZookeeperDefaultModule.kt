package misk.zookeeper

import misk.clustering.zookeeper.ZookeeperConfig
import misk.inject.KAbstractModule
import kotlin.reflect.KClass

/**
 * Provides a connection to a qualified Zookeeper server with default settings.
 *
 * Applications can install this module directly to use the default settings or provide explicit
 * bindings to override the defaults.
 */
class ZookeeperDefaultModule(
  private val config: ZookeeperConfig,
  private val qualifier: KClass<out Annotation>? = null
) : KAbstractModule() {
  override fun configure() {
    install(CuratorFrameworkModule(config, qualifier))
    install(FixedEnsembleProviderModule(config, qualifier))
    install(StaticHostModule(config, qualifier))
  }
}
