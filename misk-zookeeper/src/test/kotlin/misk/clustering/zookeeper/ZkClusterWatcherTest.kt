package misk.clustering.zookeeper

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.ServiceModule
import misk.clustering.Cluster
import misk.clustering.zookeeper.ZkClusterModule.ZkCluster
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.zookeeper.ZkClientFactory
import misk.zookeeper.ZkService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import kotlin.concurrent.thread

@MiskTest(startService = true)
class ZkClusterWatcherTest {

  @MiskTestModule private val module = Modules.combine(
      MiskTestingServiceModule(),
      ZkLeaseTestModule(),
      ZkClusterModule(Cluster.Member(name = "pod-1", ipAddress = "1.1.1.1")),
      object : KAbstractModule() {
        override fun configure() {
          install(ServiceModule<ZkClusterWatcher>()
              .dependsOn<ZkService>(ForZkLease::class))
          install(ServiceModule<ZkClusterJoiner>()
              .dependsOn<ZkService>(ForZkLease::class))
        }
      }
  )

  @Inject @ForZkLease lateinit var client: ZkClientFactory
  @Inject lateinit var serviceManager: Provider<ServiceManager>

  @Inject @ZkCluster lateinit var cluster: Cluster

  @BeforeEach
  fun initCurator() {
    client.client().blockUntilConnected(10, TimeUnit.SECONDS)
//    otherClient.client().blockUntilConnected(10, TimeUnit.SECONDS)
  }

  @Test fun asdf() {
    cluster.watch { changes ->
      println("cluster changed!!! members=${changes.snapshot.readyMembers.joinToString(",")}")
    }

    val otherDone = CountDownLatch(1)
    thread(start = true) {
      val otherJoiner = ZkClusterJoiner(
          self = Cluster.Member(name = "pod-2", ipAddress = "2.2.2.2"),
          clientFactory = client,
          serviceManager = serviceManager
      )
      Thread.sleep(2_000)
      println("**** 2 is about to join")
      otherJoiner.startAsync()
      Thread.sleep(5_000)
      println("**** 2 is about to leave")
      otherJoiner.stopAsync()
      otherDone.countDown()
    }

//    val cache = PathChildrenCache(client.client().usingNamespace("clusterMembers"), "/", false)
//    cache.listenable.addListener(PathChildrenCacheListener { _, event -> println("!!!! $event") })
//    cache.start(PathChildrenCache.StartMode.POST_INITIALIZED_EVENT)

//    while (true) {
//      println(watcher.members)
//      Thread.sleep(1_000)
//    }

    otherDone.await()
    Thread.sleep(5_000)
//
//    watcher.startAsync()
  }
}
