package misk.clustering.zookeeper

import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.utils.DefaultZookeeperFactory
import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.client.ZKClientConfig
import javax.inject.Inject
import javax.inject.Provider

internal class CuratorFrameworkProvider @Inject internal constructor(
  private val config: ZookeeperConfig
) : Provider<CuratorFramework> {

  override fun get(): CuratorFramework {
    require((config.cert_store == null && config.trust_store == null) ||
        (config.cert_store != null && config.trust_store != null)) {
      "only one of ZookeeperConfig cert_store and trust_store has been set. Must set both or neither"
    }

    var tlsEnabled = false
    if (config.cert_store != null && config.trust_store != null) {
      tlsEnabled = true
    }

    // Uses reasonable default values from http://curator.apache.org/getting-started.html
    val retryPolicy = ExponentialBackoffRetry(1000, 3)
    return CuratorFrameworkFactory.builder()
        .connectString(config.zk_connect)
        .retryPolicy(retryPolicy)
        .sessionTimeoutMs(config.session_timeout_msecs)
        .canBeReadOnly(false)
        .threadFactory(ThreadFactoryBuilder()
            .setNameFormat("zk-clustering-${config.zk_connect}")
            .build())
        .zookeeperFactory { connectString, sessionTimeout, watcher, canBeReadOnly ->
          val clientConfig = ZKClientConfig()
          if (tlsEnabled) {
            clientConfig.setProperty("zookeeper.clientCnxnSocket", "org.apache.zookeeper.ClientCnxnSocketNetty")
            clientConfig.setProperty("zookeeper.client.secure", "true")
            clientConfig.setProperty("zookeeper.ssl.keyStore.location", config.cert_store?.resource)
            clientConfig.setProperty("zookeeper.ssl.keyStore.password", config.cert_store?.passphrase)
            clientConfig.setProperty("zookeeper.ssl.trustStore.location", config.trust_store?.resource)
            clientConfig.setProperty("zookeeper.ssl.trustStore.password", config.trust_store?.passphrase)
          }
          ZooKeeper(connectString, sessionTimeout, watcher, canBeReadOnly, clientConfig)
        }
        .build()
  }
}
