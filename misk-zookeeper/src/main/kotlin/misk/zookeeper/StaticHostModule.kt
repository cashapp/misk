package misk.zookeeper

import misk.clustering.zookeeper.ZookeeperConfig
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import org.apache.zookeeper.client.ConnectStringParser
import org.apache.zookeeper.client.HostProvider
import org.apache.zookeeper.client.StaticHostProvider
import javax.inject.Provider
import kotlin.reflect.KClass

/**
 * Binds a zk [HostProvider] to a [StaticHostProvider].
 *
 * This is the default implementation used by Zookeeper.
 */
class StaticHostModule(
  private val config: ZookeeperConfig,
  private val qualifier: KClass<out Annotation>? = null
) : KAbstractModule() {
  override fun configure() {
    bind(keyOf<HostProvider>(qualifier)).toProvider(Provider {
      StaticHostProvider(ConnectStringParser(config.zk_connect).serverAddresses)
    }).asSingleton()
  }
}
