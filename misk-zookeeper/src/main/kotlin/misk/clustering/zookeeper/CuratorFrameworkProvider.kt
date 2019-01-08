package misk.clustering.zookeeper

import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import javax.inject.Inject
import javax.inject.Provider

internal class CuratorFrameworkProvider @Inject internal constructor(
  private val config: ZookeeperConfig
) : Provider<CuratorFramework> {

  override fun get(): CuratorFramework {
    if (config.cert_store != null && config.trust_store != null) {
      System.setProperty("zookeeper.clientCnxnSocket", "org.apache.zookeeper.ClientCnxnSocketNetty")
      System.setProperty("zookeeper.client.secure", "true")
      System.setProperty("zookeeper.ssl.keyStore.location", config.cert_store.resource)
      System.setProperty("zookeeper.ssl.keyStore.password", config.cert_store.passphrase ?: "changeit")
      System.setProperty("zookeeper.ssl.trustStore.location", config.trust_store.resource)
      System.setProperty("zookeeper.ssl.trustStore.password", config.trust_store.passphrase ?: "changeit")
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
        .build()
  }
}
