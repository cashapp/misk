package misk.clustering.kubernetes

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import io.kubernetes.client.models.V1Pod
import io.kubernetes.client.util.Watch
import misk.DependentService
import misk.clustering.Cluster
import misk.clustering.ClusterWatch
import misk.logging.getLogger
import org.eclipse.jetty.util.BlockingArrayQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/**
 * [KubernetesClusterService] implements the [Cluster] interface on top of the kubernetes APIs.
 * To make use of this service, pods must run as a service account that has pod list/watch/get
 * access within its own namespace
 */
@Singleton
internal class KubernetesClusterService @Inject internal constructor(
  config: KubernetesConfig
) : AbstractIdleService(), Cluster, DependentService {
  private val peersRef = AtomicReference<Set<Cluster.Member>>(setOf())
  private val running = AtomicBoolean(false)
  private val events = BlockingArrayQueue<Event>()

  override val self: Cluster.Member = Cluster.Member(config.my_pod_name, config.my_pod_ip)
  override val consumedKeys: Set<Key<*>> = setOf()
  override val producedKeys: Set<Key<*>> = setOf(
      Key.get(Cluster::class.java),
      Key.get(KubernetesClusterService::class.java)
  )
  override val peers: Set<Cluster.Member> get() = peersRef.get()

  override fun startUp() {
    log.info { "starting k8s cluster manager" }
    running.set(true)

    thread(name = "k8s-cluster-event-loop") {
      runEventLoop()
    }
  }

  override fun shutDown() {
    log.info { "shutting down k8s cluster manager" }
    events.offer(Event(Event.Type.SHUTDOWN))
  }

  override fun watch(watch: ClusterWatch) {
    events.offer(Event(watch))
  }

  internal fun clusterChanged(watchResponse: Watch.Response<V1Pod>) {
    events.offer(Event(watchResponse))
  }

  internal fun syncPoint(callback: () -> Unit) {
    events.offer(Event(callback))
  }

  /**
   * Runs the internal event loop that handles requests to add watches or cluster changes. We use
   * a single threaded event loop to ensure that watches get consistent diffs for the cluster
   * without being racy or requiring locks that span application code (which might result in
   * deadlocks). When a watch is registered, it is provided with the cluster membership as it
   * is known at that time, and then receives diffs as they arrive.
   */
  private fun runEventLoop() {
    val watches = mutableSetOf<ClusterWatch>()

    while (running.get()) {
      val event = events.take()
      when (event.type) {
        Event.Type.CLUSTER_CHANGE -> handleClusterChange(event.watchResponse!!, watches)
        Event.Type.NEW_WATCH -> handleNewWatch(event.newWatch!!, peersRef.get(), watches)
        Event.Type.SYNC_POINT -> try {
          event.syncPointCallback!!()
        } catch (th: Throwable) {
          log.error(th) { "error triggering syncpoint callback" }
        }
        Event.Type.SHUTDOWN -> running.set(false)
      }
    }
  }

  /** Handles a change to a cluster, updating the current peer set and triggering all watches */
  private fun handleClusterChange(response: Watch.Response<V1Pod>, watches: Set<ClusterWatch>) {
    val newPeers = mutableMapOf<String, Cluster.Member>()
    peersRef.get()?.forEach { newPeers[it.name] = it }

    val changes = applyChange(newPeers, response.type, response.`object`)
    if (!changes.hasDiffs) return

    peersRef.set(changes.current)

    watches.forEach {
      try {
        it(changes)
      } catch (th: Throwable) {
        log.error(th) { "error triggering watch in response to cluster change" }
      }
    }
  }

  /**
   * Handles a request for a new watch, adding it to the watch set and triggering it with
   * the current set of peers
   */
  private fun handleNewWatch(
    newWatch: ClusterWatch,
    peers: Set<Cluster.Member>,
    watches: MutableSet<ClusterWatch>
  ) {
    watches.add(newWatch)
    try {
      newWatch(Cluster.Changes(peers))
    } catch (th: Throwable) {
      log.error(th) { "error triggering watch during registration" }
    }
  }

  /** Applies an incoming change to a set of cluster members */
  private fun applyChange(
    peers: MutableMap<String, Cluster.Member>,
    changeType: String,
    pod: V1Pod
  ): Cluster.Changes {
    val member = Cluster.Member(pod.metadata.name, pod.status.podIP ?: "")

    // Don't bother to include ourselves in the peer list
    if (member.name == self.name) return Cluster.Changes(peers.values.toSet())

    return when (changeType) {
      CHANGE_TYPE_ADDED, CHANGE_TYPE_MODIFIED -> {
        val isReady = pod.status.containerStatuses?.all { it.isReady } ?: false
        if (isReady && member.ipAddress.isNotBlank()) {
          log.info { "added peer ${member.name}" }
          peers[member.name] = member
          Cluster.Changes(peers.values.toSet(), added = setOf(member))
        } else if (peers.containsKey(member.name)) {
          log.info { "peer ${member.name} no longer ready" }
          peers.remove(member.name)
          Cluster.Changes(peers.values.toSet(), removed = setOf(member))
        } else Cluster.Changes(peers.values.toSet())
      }

      CHANGE_TYPE_DELETED -> {
        if (peers.containsKey(member.name)) {
          log.info { "deleted peer ${member.name}" }
          peers.remove(member.name)
          Cluster.Changes(peers.values.toSet(), removed = setOf(member))
        } else Cluster.Changes(peers.values.toSet())
      }
      else -> Cluster.Changes(peers.values.toSet())
    }
  }

  /**
   * An internal event handled by the cluster service. All interactions with the cluster service
   * are dispatched as events polled by the event loop]
   */
  data class Event(
    val type: Type,
    val watchResponse: Watch.Response<V1Pod>? = null,
    val newWatch: ClusterWatch? = null,
    val syncPointCallback: (() -> Unit)? = null
  ) {
    enum class Type {
      // Sent when a cluster changes, contains a watch response
      CLUSTER_CHANGE,

      // Sent when a new watch is registered, contains a ClusterWatch
      NEW_WATCH,

      // Sent when a sync point is requested. Allows application code to get a callback when
      // all of the items currently on the queue have been processed. Useful for testing, to
      // ensure that we wait until we've reach a well known state where all events we've submitted
      // have been completed.
      SYNC_POINT,

      // Sent to shutdown the service, contains nothing
      SHUTDOWN
    }

    constructor(syncPointCallback: () -> Unit) :
        this(Type.SYNC_POINT, syncPointCallback = syncPointCallback)

    constructor(newWatch: ClusterWatch) : this(Type.NEW_WATCH, newWatch = newWatch)

    constructor(watchResponse: Watch.Response<V1Pod>) :
        this(Type.CLUSTER_CHANGE, watchResponse = watchResponse)
  }

  companion object {
    private val log = getLogger<KubernetesClusterService>()

    const val CHANGE_TYPE_MODIFIED = "MODIFIED"
    const val CHANGE_TYPE_DELETED = "DELETED"
    const val CHANGE_TYPE_ADDED = "ADDED"
  }
}