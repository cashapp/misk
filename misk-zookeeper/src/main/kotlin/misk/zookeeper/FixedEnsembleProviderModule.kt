package misk.zookeeper

import misk.clustering.zookeeper.ZookeeperConfig
import misk.inject.KAbstractModule
import misk.inject.keyOf
import org.apache.curator.ensemble.EnsembleProvider
import org.apache.curator.ensemble.fixed.FixedEnsembleProvider
import javax.inject.Provider
import kotlin.reflect.KClass

class FixedEnsembleProviderModule(
  private val config: ZookeeperConfig,
  private val qualifier: KClass<out Annotation>?
) : KAbstractModule() {
  override fun configure() {
    bind(keyOf<EnsembleProvider>(qualifier)).toProvider(object : Provider<EnsembleProvider> {
      override fun get(): EnsembleProvider {
        return FixedEnsembleProvider(config.zk_connect)
      }
    })
  }
}
