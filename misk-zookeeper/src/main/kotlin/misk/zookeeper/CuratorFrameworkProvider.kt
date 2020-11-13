package misk.zookeeper

import com.google.common.util.concurrent.ThreadFactoryBuilder
import misk.clustering.zookeeper.ZookeeperConfig
import misk.clustering.zookeeper.asZkPath
import org.apache.curator.ensemble.EnsembleProvider
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.framework.api.ACLProvider
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.ZooDefs
import org.apache.zookeeper.ZooDefs.Ids.ANYONE_ID_UNSAFE
import org.apache.zookeeper.ZooDefs.Ids.AUTH_IDS
import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.client.HostProvider
import org.apache.zookeeper.client.ZKClientConfig
import org.apache.zookeeper.data.ACL
import java.util.Collections
import javax.inject.Inject
import javax.inject.Provider

// Directory where service-specific directories are created
const val SERVICES_NODE = "services"

const val DEFAULT_PERMS = ZooDefs.Perms.READ or
    ZooDefs.Perms.WRITE or
    ZooDefs.Perms.CREATE or
    ZooDefs.Perms.DELETE

const val SHARED_DIR_PERMS = ZooDefs.Perms.READ or ZooDefs.Perms.WRITE or ZooDefs.Perms.CREATE

internal class CuratorFrameworkProvider @Inject internal constructor(
  private val config: ZookeeperConfig,
  private val ensembleProvider: Provider<EnsembleProvider>,
  private val hostProvider: Provider<HostProvider>
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
        .retryPolicy(retryPolicy)
        .sessionTimeoutMs(config.session_timeout_msecs)
        .canBeReadOnly(false)
        .threadFactory(ThreadFactoryBuilder()
            .setNameFormat("zk-${config.zk_connect}")
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
          ZooKeeper(connectString, sessionTimeout, watcher, canBeReadOnly, hostProvider.get(), clientConfig)
        }
        .aclProvider(object : ACLProvider {
          override fun getDefaultAcl(): List<ACL> {
            // Default ACL allows clients CRUD operations on znodes this service has created.
            // Service identification comes from the client cert
            return Collections.singletonList(ACL(DEFAULT_PERMS, AUTH_IDS))
          }

          override fun getAclForPath(path: String?): List<ACL> {
            // Shared directories (i.e. /services) need to be created in such a way that apps can
            // create them if they're missing and read or write to them, but an app should not be
            // able to delete a shared directory as it contains more than just that app's data.
            if (path == SERVICES_NODE.asZkPath) {
              return Collections.singletonList(ACL(SHARED_DIR_PERMS, ANYONE_ID_UNSAFE))
            }

            return defaultAcl
          }
        })
        .ensembleProvider(ensembleProvider.get())
        .build()
  }
}
