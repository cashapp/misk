package misk.clustering.zookeeper

import com.google.common.util.concurrent.AbstractExecutionThreadService
import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.ServiceManager
import misk.clustering.Cluster
import misk.logging.getLogger
import misk.zookeeper.ZkClientFactory
import org.apache.curator.framework.recipes.cache.PathChildrenCache
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener
import org.apache.curator.framework.recipes.nodes.PersistentNode
import org.apache.zookeeper.CreateMode
import java.util.concurrent.CountDownLatch
import javax.inject.Provider

private fun Cluster.Member.toBytes(): ByteArray {
  return "pod=$name,podIp=$ipAddress".toByteArray(Charsets.UTF_8)
}

private fun clusterMemberFromBytes(bytes: ByteArray): Cluster.Member {
  val data = bytes.toString(Charsets.UTF_8).split(",").map { it.split("=")[1] }
  return Cluster.Member(name = data[0], ipAddress = data[1])
}

class ZkClusterJoiner constructor(
  private val self: Cluster.Member,
  clientFactory: ZkClientFactory,
  private val serviceManager: Provider<ServiceManager>
) : AbstractExecutionThreadService() {

  private val shutdownWaiter = CountDownLatch(1)

  private val client = lazy {
    clientFactory.client().usingNamespace("clusterMembers")
  }

  private val node = lazy {
    PersistentNode(
        client.value, CreateMode.EPHEMERAL, false, self.name.asZkPath, self.toBytes())
  }

  override fun startUp() {
  }

  override fun run() {
    logger.info("waiting for all services to be healthy before joining cluster")
    serviceManager.get().awaitHealthy()

    logger.info("joining cluster")
    node.value.start()
//    node.value.waitForInitialCreate(1, TimeUnit.SECONDS)

    shutdownWaiter.await()
  }

  override fun triggerShutdown() {
    shutdownWaiter.countDown()

    logger.info("leaving cluster")
    node.value.close()
  }

  companion object {
    private val logger = getLogger<ZkClusterJoiner>()
  }
}

class ZkClusterWatcher constructor(
  clientFactory: ZkClientFactory,
  private val onChange: (added: Set<Cluster.Member>, removed: Set<Cluster.Member>) -> Unit = { _, _ -> }
) : AbstractIdleService() {

  private val client = lazy {
    clientFactory.client().usingNamespace("clusterMembers")
  }

  private val clusterMembersCache = lazy {
    PathChildrenCache(client.value, "/", true)
  }

  override fun startUp() {
    clusterMembersCache.value.listenable.addListener(PathChildrenCacheListener { _, event ->
      logger.info("got cluster watch event ${event.type}")

      val oldMembers = snapshot
      val members = LinkedHashSet(clusterMembersCache.value.currentData
          .map { clusterMemberFromBytes(it.data) }
          .sortedBy { it.name })

      if (oldMembers != members) {
        snapshot = members

        val added = members - oldMembers
        val removed = oldMembers - members

        fun names(all: Set<Cluster.Member>) = all.joinToString(",") { it.name }
        logger.info("cluster changed. " +
            "members=${names(members)} added=${names(added)} removed=${names(removed)}")

        try {
          onChange(added, removed)
        } catch (e: Exception) {
          logger.error(e) { "error notifying of cluster change" }
        }
      }
    })

    clusterMembersCache.value.start(PathChildrenCache.StartMode.POST_INITIALIZED_EVENT)
  }

  override fun shutDown() {
    clusterMembersCache.value.close()
  }

  @Volatile var snapshot: Set<Cluster.Member> = linkedSetOf()
    private set(value) {
      field = value
    }

  companion object {
    private val logger = getLogger<ZkClusterWatcher>()
  }
}
