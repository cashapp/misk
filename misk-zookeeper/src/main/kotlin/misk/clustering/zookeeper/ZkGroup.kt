package misk.clustering.zookeeper

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Provides
import misk.ServiceModule
import misk.clustering.Cluster
import misk.inject.KAbstractModule
import misk.logging.getLogger
import misk.zookeeper.ZkClientFactory
import misk.zookeeper.ZkService
import org.apache.curator.framework.recipes.cache.PathChildrenCache
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener
import org.apache.curator.framework.recipes.nodes.PersistentNode
import org.apache.zookeeper.CreateMode
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

class ZkGroupModule : KAbstractModule() {
  override fun configure() {
    requireBinding(Cluster::class.java)

    install(ServiceModule<ZkGroupManager>()
        .dependsOn<ZkService>(ForZkLease::class))

    bind<GroupManager>().to<ZkGroupManager>()
  }

  @Provides
  @Singleton
  fun zkGroupManager(
    cluster: Cluster,
    @ForZkLease clientFactory: ZkClientFactory
  ): ZkGroupManager = ZkGroupManager(cluster, clientFactory)
}

typealias Watcher = (before: Set<Group.Member>, after: Set<Group.Member>) -> Boolean

interface Group {
  data class Member(
    val node: Cluster.Member,
    val state: String
  )

  val name: String

  fun join(state: String)

  fun leave()

  fun members(): Set<Member>

  fun watch(watcher: (before: Set<Member>, after: Set<Member>) -> Boolean)

  fun waitUntil(predicate: (Set<Member>) -> Boolean) {
    val lock = Object()
    var done = false
    val doneLatch = CountDownLatch(1)
    watch { _, after ->
      synchronized(lock) {
        if (done || predicate(after)) {
          done = true
          doneLatch.countDown()
          false
        } else {
          true
        }
      }
    }

    synchronized(lock) {
      if (done || predicate(members())) {
        done = true
        return
      }
    }

    doneLatch.await()
  }
}

interface GroupManager {
  fun group(name: String): Group
}

class ZkGroupManager(
  cluster: Cluster,
  private val clientFactory: ZkClientFactory
) : GroupManager, AbstractIdleService() {

  private var done = false
  private val self = cluster.snapshot.self
  private val groups = mutableSetOf<ZkGroup>()

  override fun startUp() {}

  @Synchronized
  override fun shutDown() {
    done = true

    groups.forEach { it.close() }
  }

  @Synchronized
  override fun group(name: String): ZkGroup {
    check(!done) { "cannot get group when shutting down" }

    val group = ZkGroup(
        self = self,
        name = name,
        clientFactory = clientFactory
    )

    groups.add(group)
    return group
  }
}

class ZkGroup(
  private val self: Cluster.Member,
  override val name: String,
  @ForZkLease clientFactory: ZkClientFactory
) : Group, Closeable {

  private var node: PersistentNode? = null

  private val client =
      clientFactory.client().usingNamespace("groups/$name")
  private var watchers = setOf<Watcher>()
  @Volatile private var members = setOf<Group.Member>()

  private val cache = PathChildrenCache(client, "/", true)
      .let { cache ->
        cache.listenable.addListener(PathChildrenCacheListener { _, event ->
          val before = members
          val after = cache.currentData
              .map { groupMemberFromBytes(it.data) }.toSet()

          if (before != after) {
            logger.info("zk group changed. group=$name members=${
            after.joinToString(",") { it.node.name }}")

            members = after
            val watchersToRemove = synchronized(this) { watchers }
                .filterNot {
                  try {
                    it(before, after)
                  } catch (e: Exception) {
                    logger.error(e) { "error calling watcher" }
                    true
                  }
                }
            if (watchersToRemove.isNotEmpty()) {
              synchronized(this) {
                watchers = watchers - watchersToRemove
              }
            }
          }
        })
        cache.start(PathChildrenCache.StartMode.POST_INITIALIZED_EVENT)
        cache
      }

  @Synchronized
  override fun join(state: String) {
    val member = Group.Member(node = self, state = state)

    if (node?.let { it.data = member.toBytes() } != null) {
      return
    }

    node = PersistentNode(
        client, CreateMode.EPHEMERAL, false, self.name.asZkPath, member.toBytes()).let { node ->
      node.start()
      node.waitForInitialCreate(5, TimeUnit.SECONDS)
      node
    }
  }

  @Synchronized
  override fun leave() {
    try {
      node?.close()
    } finally {
      node = null
    }
  }

  override fun close() {
    cache.close()
    leave()
  }

  override fun members(): Set<Group.Member> = members

  @Synchronized
  override fun watch(watcher: Watcher) {
    watchers = watchers + watcher
  }

  companion object {
    private val logger = getLogger<ZkGroup>()

    private fun Group.Member.toBytes(): ByteArray {
      return "pod=${node.name},podIp=${node.ipAddress},state=$state".toByteArray(Charsets.UTF_8)
    }

    private fun groupMemberFromBytes(bytes: ByteArray): Group.Member {
      val data = bytes.toString(Charsets.UTF_8).split(",").map { it.split("=")[1] }
      return Group.Member(
          node = Cluster.Member(name = data[0], ipAddress = data[1]),
          state = data[2]
      )
    }
  }
}
