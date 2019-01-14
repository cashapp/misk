package misk.clustering.zookeeper

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import com.google.inject.Key
import misk.DependentService
import misk.clustering.fake.FakeClusterModule
import misk.config.AppName
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.logging.getLogger
import misk.security.ssl.CertStoreConfig
import misk.security.ssl.SslLoader.Companion.FORMAT_JKS
import misk.security.ssl.TrustStoreConfig
import javax.inject.Singleton

internal class ZkTestModule : KAbstractModule() {
  override fun configure() {
    val keystorePath = this::class.java.getResource("/zookeeper/keystore.jks").path
    val truststrorePath = this::class.java.getResource("/zookeeper/truststore.jks").path
    bind<ZookeeperConfig>().toInstance(ZookeeperConfig(
        zk_connect = "127.0.0.1:$zkPortKey",
        cert_store = CertStoreConfig(keystorePath, "changeit", FORMAT_JKS),
        trust_store = TrustStoreConfig(truststrorePath, "changeit", FORMAT_JKS)))
    bind<String>().annotatedWith<AppName>().toInstance("my-app")

    multibind<Service>().to<StartZookeeperService>()
    install(FakeClusterModule())
    install(ZookeeperModule())
  }

  @Singleton
  class StartZookeeperService : AbstractIdleService(), DependentService {
    override val consumedKeys: Set<Key<*>> = setOf()
    override val producedKeys: Set<Key<*>> = setOf(startZkServiceKey)
    private val sharedZookeeper = EmbeddedZookeeper(zkPortKey)

    override fun startUp() {
      log.info { "starting zk instance " }
      sharedZookeeper.start()
    }

    override fun shutDown() {
      log.info { "stopping zk instance" }
      sharedZookeeper.stop()
    }
  }

  companion object {
    private const val zkPortKey = 29000
    private val log = getLogger<ZkTestModule>()
    private val startZkServiceKey = keyOf<StartZookeeperService>()
  }
}
