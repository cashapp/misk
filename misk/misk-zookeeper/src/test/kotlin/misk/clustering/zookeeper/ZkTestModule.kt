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
import java.util.concurrent.atomic.AtomicBoolean
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

  @Singleton class StartZookeeperService : AbstractIdleService(), DependentService {
    override val consumedKeys: Set<Key<*>> = setOf()
    override val producedKeys: Set<Key<*>> = setOf(startZkServiceKey)

    override fun startUp() {
      if (hasStarted.compareAndSet(false, true)) {
        log.info { "starting zk instance" }
        sharedZookeeper.start()
        Runtime.getRuntime().addShutdownHook(Thread {
          log.info { "stopping zk instance" }
          sharedZookeeper.stop()
        })
      } else {
        log.info { "zk instance already running" }
      }
    }

    override fun shutDown() {
      // Shutdown happens as a runtime shutdown hook
      log.info { "zk instance shuts down on runtime shutdown" }
    }

    companion object {
      private val sharedZookeeper = EmbeddedZookeeper(zkPortKey)
      private var hasStarted = AtomicBoolean(false)
    }
  }

  companion object {
    private const val zkPortKey = 29000
    private val log = getLogger<ZkTestModule>()
    private val startZkServiceKey = keyOf<StartZookeeperService>()
  }
}
