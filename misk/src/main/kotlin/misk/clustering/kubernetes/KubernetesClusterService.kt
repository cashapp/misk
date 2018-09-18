package misk.clustering.kubernetes

import com.google.common.util.concurrent.AbstractExecutionThreadService
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

/**
 * [KubernetesClusterService] implements the [Cluster] interface on top of the kubernetes APIs.
 * To make use of this service, pods must run as a service account that has pod list/watch/get
 * access within its own namespace
 */
@Singleton
internal class KubernetesClusterService @Inject internal constructor(
  config: KubernetesConfig
) : AbstractExecutionThreadService(), Cluster, DependentService {

  private val self: Cluster.Member = Cluster.Member(config.my_pod_name, config.my_pod_ip)
  private val snapshotRef = AtomicReference<Cluster.Snapshot>(Cluster.Snapshot(
      self = self,
      selfReady = false,
      readyMembers = setOf()
  ))
  private val running = AtomicBoolean(false)
  private val events = BlockingArrayQueue<Event>()

  override val snapshot: Cluster.Snapshot get() = snapshotRef.get()
  override val consumedKeys: Set<Key<*>> = setOf()
  override val producedKeys: Set<Key<*>> = setOf(
      Key.get(Cluster::class.java),
      Key.get(KubernetesClusterService::class.java)
  )

  /**
   * Runs the internal event loop that handles requests to add watches or cluster changes. We use
   * a single threaded event loop to ensure that watches get consistent diffs for the cluster
   * without being racy or requiring locks that span application code (which might result in
   * deadlocks). When a watch is registered, it is provided with the cluster membership as it
   * is known at that time, and then receives diffs as they arrive.
   */
  override fun run() {
    val watches = mutableSetOf<ClusterWatch>()

    while (running.get()) {
      val event = events.take()
      when (event.type) {
        Event.Type.CLUSTER_CHANGE -> handleClusterChange(event.watchResponse!!, watches)
        Event.Type.NEW_WATCH -> handleNewWatch(event.newWatch!!, watches)
        Event.Type.SYNC_POINT -> try {
          event.syncPointCallback!!()
        } catch (th: Throwable) {
          log.error(th) { "error triggering syncpoint callback" }
        }
        Event.Type.SHUTDOWN -> running.set(false)
      }
    }
  }

  override fun startUp() {
    log.info { "starting k8s cluster manager" }
    running.set(true)
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

  /** Handles a change to a cluster, updating the current peer set and triggering all watches */
  private fun handleClusterChange(response: Watch.Response<V1Pod>, watches: Set<ClusterWatch>) {
    val currentSnapshot = snapshotRef.get()
    val changes = applyChange(currentSnapshot, response.type, response.`object`)
    if (!changes.hasDiffs) return

    snapshotRef.set(changes.snapshot)
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
    newWatch: ClusterWatch, watches: MutableSet<ClusterWatch>
  ) {
    watches.add(newWatch)
    try {
      newWatch(Cluster.Changes(snapshot))
    } catch (th: Throwable) {
      log.error(th) { "error triggering watch during registration" }
    }
  }

  /** Applies an incoming change to a set of cluster members */
  private fun applyChange(
    currentSnapshot: Cluster.Snapshot,
    changeType: String,
    pod: V1Pod
  ): Cluster.Changes {
    val changeTracker = ClusterChangeTracker(currentSnapshot)
    val member = Cluster.Member(pod.metadata.name, pod.status.podIP ?: "")

    return when (changeType) {
      CHANGE_TYPE_ADDED, CHANGE_TYPE_MODIFIED ->
        if (pod.isReady) changeTracker.memberReady(member)
        else changeTracker.memberNotReady(member)
      CHANGE_TYPE_DELETED -> changeTracker.memberNotReady(member)
      else -> Cluster.Changes(currentSnapshot)
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

private val V1Pod.isReady: Boolean
  get() {
    if (status.containerStatuses == null) return false
    if (status.containerStatuses.any { !it.isReady }) return false
    return status.podIP?.isNotEmpty() ?: false
  }

private class ClusterChangeTracker(private val snapshot: Cluster.Snapshot) {
  private val readyMembers = snapshot.readyMembers.map { it.name to it }.toMap()

  fun memberReady(member: Cluster.Member): Cluster.Changes {
    val existingReadyMember = readyMembers[member.name]
    if (existingReadyMember != null && existingReadyMember.ipAddress == member.ipAddress) {
      // We already know about this as a ready member, so just return the current snapshot
      return Cluster.Changes(snapshot)
    }

    // Either the member wasn't ready or the IP address changed, so add it as a ready member
    return Cluster.Changes(snapshot = Cluster.Snapshot(
        readyMembers = (readyMembers - member.name).values.toSet() + member,
        self = snapshot.self,
        selfReady = if (member.name == snapshot.self.name) true else snapshot.selfReady
    ), added = setOf(member))
  }

  fun memberNotReady(member: Cluster.Member): Cluster.Changes {
    // If we're not currently tracking this member as ready, just return the current snapshot
    if (!readyMembers.containsKey(member.name)) return Cluster.Changes(snapshot)

    return Cluster.Changes(snapshot = Cluster.Snapshot(
        readyMembers = (readyMembers - member.name).values.toSet(),
        self = snapshot.self,
        selfReady = if (member.name == snapshot.self.name) false else snapshot.selfReady
    ), removed = setOf(member))
  }
}

