package misk.clustering

import com.google.common.util.concurrent.AbstractExecutionThreadService
import wisp.logging.getLogger
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Singleton

/**
 * A [DefaultCluster] is the default implementation of the [Cluster], which relies on an outside
 * source such as a cluster watcher to inform it as members become ready or not ready. The
 * [DefaultCluster] handles computing the actual cluster changes, and managing application watches
 * that need to be triggered as the cluster changes.
 */
@Singleton
class DefaultCluster(
  self: Cluster.Member,
  private val newResourceMapperFn: (members: Set<Cluster.Member>) -> ClusterResourceMapper =
    { ClusterHashRing(it) }
) : AbstractExecutionThreadService(), Cluster, ClusterService {
  private val snapshotRef = AtomicReference<Cluster.Snapshot>(
    Cluster.Snapshot(
      self = self,
      selfReady = false,
      readyMembers = setOf(),
      resourceMapper = newResourceMapper(setOf())
    )
  )
  private val running = AtomicBoolean(false)
  private val actions = LinkedBlockingQueue<(MutableSet<ClusterWatch>) -> Unit>()

  override val snapshot: Cluster.Snapshot get() = snapshotRef.get()

  /**
   * Runs the internal event loop that handles requests to add watches or cluster changes. We use
   * a single threaded event loop to ensure that watches get consistent diffs for the cluster
   * without being racy or requiring locks that span application code (which might result in
   * deadlocks). When a watch is registered, it is provided with the cluster membership as it
   * is known at that time, and then receives diffs as they arrive.
   */
  override fun run() {
    log.info { "cluster manager worker running" }
    val watches = mutableSetOf<ClusterWatch>()

    while (running.get()) {
      try {
        val action = actions.take()
        action(watches)
      } catch (th: Throwable) {
        log.error(th) { "error calling event queue action" }
      }
    }
  }

  override fun startUp() {
    log.info { "starting cluster manager" }
    running.set(true)
  }

  override fun triggerShutdown() {
    log.info { "shutting down cluster manager" }
    running.set(false)
    actions.add { _ -> } // empty action to up the action queue if it is empty
  }

  /** Adds a new cluster watch */
  override fun watch(watch: ClusterWatch) {
    actions.add { watches ->
      log.info { "registering new watch" }
      watches.add(watch)
      watch(Cluster.Changes(snapshot))
    }
  }

  /** Triggers a change to the cluster in response members becoming ready or not ready */
  fun clusterChanged(
    membersBecomingReady: Set<Cluster.Member> = setOf(),
    membersBecomingNotReady: Set<Cluster.Member> = setOf()
  ) {
    actions.add { watches ->
      val changeTracker = ClusterChangeTracker(this)
      membersBecomingReady.forEach { changeTracker.memberReady(it) }
      membersBecomingNotReady.forEach { changeTracker.memberNotReady(it) }
      val changes = changeTracker.computeChanges()

      if (changes.hasDiffs) {
        snapshotRef.set(changes.snapshot)
        watches.forEach {
          try {
            it(changes)
          } catch (th: Throwable) {
            log.error(th) { "error triggering watch in response to cluster change" }
          }
        }
      }
    }
  }

  /**
   * Triggers a callback once all of the actions on the queue have been processed. Useful
   * for writing deterministic tests
   */
  fun syncPoint(callback: () -> Unit) {
    actions.add { _ -> callback() }
  }

  override fun newResourceMapper(readyMembers: Set<Cluster.Member>) =
    newResourceMapperFn(readyMembers)

  /**
   * [ClusterChangeTracker] is an internal helper class used to track diffs to a cluster as
   * members become ready or not ready
   */
  private class ClusterChangeTracker(
    private val cluster: Cluster,
    private val snapshot: Cluster.Snapshot = cluster.snapshot
  ) {
    private val readyMembers = snapshot.readyMembers.map { it.name to it }.toMap().toMutableMap()
    private val membersAdded = mutableSetOf<Cluster.Member>()
    private val membersRemoved = mutableSetOf<Cluster.Member>()

    fun memberReady(member: Cluster.Member) {
      if (readyMembers[member.name] == member) {
        // We already know about this as a ready member and all of its properties are the same,
        // so no need to apply any changes
        return
      }

      readyMembers[member.name] = member
      membersAdded += member
    }

    fun memberNotReady(member: Cluster.Member) {
      if (readyMembers.remove(member.name) != null) {
        membersRemoved += member
      }
    }

    fun computeChanges(): Cluster.Changes {
      val newReadyMembers = readyMembers.values.toSet()
      return Cluster.Changes(
        Cluster.Snapshot(
          self = snapshot.self,
          selfReady = readyMembers.containsKey(snapshot.self.name),
          readyMembers = newReadyMembers,
          resourceMapper = cluster.newResourceMapper(newReadyMembers)
        ),
        added = membersAdded.toSet(), removed = membersRemoved.toSet()
      )
    }
  }

  companion object {
    val log = getLogger<DefaultCluster>()
  }
}
