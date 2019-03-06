package misk.clustering.zookeeper

import com.google.common.util.concurrent.Service
import com.google.inject.Key
import com.google.inject.Provides
import com.google.inject.util.Modules
import misk.DependentService
import misk.clustering.fake.FakeClusterModule
import misk.concurrent.ExplicitReleaseDelayQueue
import misk.config.AppName
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.logging.getLogger
import misk.security.ssl.CertStoreConfig
import misk.security.ssl.SslLoader.Companion.FORMAT_JKS
import misk.security.ssl.TrustStoreConfig
import misk.service.CachedTestService
import misk.tasks.DelayedTask
import misk.tasks.RepeatedTaskQueue
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

internal class ZkTestModule : KAbstractModule() {
  override fun configure() {
    val keystorePath = this::class.java.getResource("/zookeeper/keystore.jks").path
    val truststrorePath = this::class.java.getResource("/zookeeper/truststore.jks").path
    bind<String>().annotatedWith<AppName>().toInstance("my-app")

    multibind<Service>().to<StartZookeeperService>()
    install(FakeClusterModule())
    install(Modules.override(ZookeeperModule(ZookeeperConfig(
        zk_connect = "127.0.0.1:$zkPortKey",
        cert_store = CertStoreConfig(keystorePath, "changeit", FORMAT_JKS),
        trust_store = TrustStoreConfig(truststrorePath, "changeit", FORMAT_JKS))))
        .with(object : KAbstractModule() {
      override fun configure() {}

      @Provides @ForZkLease
      fun provideTaskQueue(
        clock: Clock,
        @ForZkLease delayQueue: ExplicitReleaseDelayQueue<DelayedTask>
      ): RepeatedTaskQueue {
        return RepeatedTaskQueue.forTesting("zk-lease-poller", clock, delayQueue)
      }

      @Provides @ForZkLease
      fun provideDelayQueue(): ExplicitReleaseDelayQueue<DelayedTask> = ExplicitReleaseDelayQueue()
    }))
  }

  @Singleton
  class StartZookeeperService @Inject constructor() : CachedTestService(), DependentService {
    override val consumedKeys: Set<Key<*>> = setOf()
    override val producedKeys: Set<Key<*>> = setOf(startZkServiceKey)

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
    private val log = getLogger<ZkTestModule>()
    private val startZkServiceKey = keyOf<StartZookeeperService>()
  }
}
