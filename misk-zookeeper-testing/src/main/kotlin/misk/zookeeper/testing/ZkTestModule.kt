package misk.zookeeper.testing

import misk.ServiceModule
import misk.clustering.zookeeper.ZookeeperConfig
import misk.config.AppName
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.security.ssl.CertStoreConfig
import misk.security.ssl.SslLoader
import misk.security.ssl.TrustStoreConfig
import misk.service.CachedTestService
import misk.zookeeper.ZkClientFactory
import misk.zookeeper.ZookeeperDefaultModule
import org.apache.curator.framework.CuratorFramework
import wisp.containers.ContainerUtil
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

class ZkTestModule(
  private val qualifier: KClass<out Annotation>?
) : KAbstractModule() {
  override fun configure() {
    val keystorePath = this::class.java.getResource("/zookeeper/keystore.jks").path
    val truststorePath = this::class.java.getResource("/zookeeper/truststore.jks").path
    val config = ZookeeperConfig(
        zk_connect = "${ContainerUtil.dockerTargetOrLocalIp()}:$zkPortKey",
        cert_store = CertStoreConfig(keystorePath, "changeit", SslLoader.FORMAT_JKS),
        trust_store = TrustStoreConfig(truststorePath, "changeit", SslLoader.FORMAT_JKS))

    install(ZookeeperDefaultModule(config, qualifier))

    install(ServiceModule<StartZookeeperService>(qualifier))
    bind(keyOf<StartZookeeperService>(qualifier)).toInstance(StartZookeeperService())
    val curator = getProvider(keyOf<CuratorFramework>(qualifier))
    bind(keyOf<ZkClientFactory>(qualifier)).toProvider(object : Provider<ZkClientFactory> {
      @Inject @AppName private lateinit var app: String
      override fun get(): ZkClientFactory {
        return ZkClientFactory(app, curator.get())
      }
    })
  }

  /**
   * The same zookeeper instance is used for all zookeeper bindings to speed up tests.
   */
  private class StartZookeeperService : CachedTestService() {
    override fun actualStartup() {
      sharedZookeeper.start()
    }

    override fun actualShutdown() {
      sharedZookeeper.stop()
    }

    companion object {
      private val sharedZookeeper = EmbeddedZookeeper(zkPortKey)
    }
  }

  companion object {
    private const val zkPortKey = 29000
  }
}
