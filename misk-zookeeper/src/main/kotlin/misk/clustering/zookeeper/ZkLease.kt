package misk.clustering.zookeeper

import misk.clustering.Cluster
import misk.clustering.NoMembersAvailableException
import misk.clustering.weights.ClusterWeightProvider
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException
import wisp.lease.Lease
import wisp.logging.getLogger
import java.util.concurrent.locks.ReentrantLock
import javax.annotation.concurrent.GuardedBy
import kotlin.concurrent.withLock

/**
 * Zookeeper based load balanced lease.
 *
 * <p>Multiple servers use a [misk.clustering.ClusterResourceMapper] to determine which process should
 * own a lease. As long as each process has the same view of the cluster, this mapping will be
 * consistent and processes will not actively compete for the same lease.
 *
 * <p>A lease is acquired by creating an ephemeral node, if it doesn't already exist. Once acquired,
 * it will not be released until the process deletes the node. Each time [checkHeld] is called, a
 * check is made against the current process hash to determine if the lease should still hold the
 * lease, and if not, it will release the lease, allowing the correctly hashed process to acquire
 * the lease subsequently.
 *
 * <p> Once acquired, the lease will generally remain held as long as the lease holder process
 * remains running. However, under certain conditions, such as a network partition or a
 * substantial full JVM pause, the lease may expire if zookeeper doesn't hear from client before
 * the zookeeper session timeout passes. This means that under certain circumstances, the
 * leadership durability is tied to the zookeeper session timeout.
 *
 * <p>
 * Users of this lease framework should avoid creating tasks that have a duration longer than
 * the session timeout or they risk having a split brain running two leader tasks on different hosts
 * simultaneously.
 */
internal class ZkLease(
  ownerName: String,
  private val manager: ZkLeaseManager,
  private val leaseResourceName: String,
  private val clusterWeight: ClusterWeightProvider,
  override val name: String
) : Lease {

  enum class Status {
    UNKNOWN,
    NOT_HELD,
    HELD,
    CLOSED
  }

  @GuardedBy("lock") private var status: Status = Status.UNKNOWN
  @GuardedBy("lock") private val listeners = mutableListOf<Lease.StateChangeListener>()
  private val lock = ReentrantLock()
  private val leaseData = ownerName.toByteArray(Charsets.UTF_8)
  private val leaseZkPath = name.asZkPath

  override fun checkHeld(): Boolean {
    try {
      if (!manager.isRunning || !manager.isConnected) {
        return false
      }

      return lock.withLock {
        if (shouldHoldLease()) {
          when (status) {
            // We already own the lease
            Status.HELD -> true

            // We don't own the lease but should - try to acquire it
            Status.UNKNOWN, Status.NOT_HELD -> tryAcquireLeaseNode()

            // The entire leasing system is shutting down so we shouldn't attempt to hold leases
            Status.CLOSED -> false
          }
        } else {
          // We shouldn't own the lease
          status = if (release()) Status.NOT_HELD else Status.UNKNOWN
          false
        }
      }
    } catch (e: Exception) {
      log.error(e) { "unexpected exception checking if lease $name is held" }
      return false
    } catch (e: Error) {
      log.error(e) { "unexpected error checking if lease $name is held" }
      return false
    }
  }

  /**
   * Attempts to acquire the lock on the lease.  If the lock was not already held and the lock
   * was successfully obtained, listeners should be notified.
   *
   * @return true if this process acquires the lease.
   */
  override fun acquire(): Boolean {
    return checkHeld()
  }

  override fun addListener(listener: Lease.StateChangeListener) {
    lock.withLock {
      listeners.add(listener)
      when (status) {
        // We already own the lease and should tell the listener
        Status.HELD -> listener.afterAcquire(this)
        // We don't know if we own the lease, so check
        Status.UNKNOWN -> checkHeld()
        else -> {
        }
      }
    }
  }

  fun close() {
    lock.withLock {
      if (status != Status.NOT_HELD) release()
      status = Status.CLOSED
    }
  }

  fun connectionLost() {
    lock.withLock {
      // We're disconnected from the cluster, so we don't know the state of the lease. Zk will
      // automatically expire the lease if we remain disconnected for too long, and in the
      // meanwhile we should act as though the lease has been lost
      if (status != Status.CLOSED) status = Status.UNKNOWN
    }
  }

  override fun release(): Boolean {
    try {
      if (checkLeaseNodeExists() && checkLeaseDataMatches()) {
        notifyBeforeRelease()
        // Lease exists in zk and we own it, so delete it
        manager.client.value.delete().guaranteed().forPath(leaseZkPath)
        log.info { "released lease $name" }
      }

      return true
    } catch (e: KeeperException.NoNodeException) {
      // ignore, node already deleted possibly due to race condition, such as when we shutdown
      // while the node is already being released due to cluster change or disconnection
      return true
    } catch (e: Exception) {
      log.warn(e) {
        "received unexpected exception while releasing lease $name, status is ${Status.UNKNOWN}"
      }
      return false
    }
  }

  private fun tryAcquireLeaseNode(): Boolean {
    // See if we still have the lease according to zookeeper - we may have forgotten this
    // as a result of a temporary disconnect / reconnect to zookeeper
    if (checkLeaseNodeExists()) {
      if (checkLeaseDataMatches()) {
        log.info { "reclaiming currently held lease $name" }
        status = Status.HELD
        notifyAfterAcquire()
        return true
      }

      leaseHeldByAnother()
      return false
    }

    // The zookeeper node representing the lease does not exist, so try to create it to acquire
    // the lease
    try {
      manager.client.value.create()
        .creatingParentsIfNeeded()
        .withMode(CreateMode.EPHEMERAL)
        .forPath(leaseZkPath, leaseData)
      status = Status.HELD
      log.info { "acquired lease $name" }
      notifyAfterAcquire()
      return true
    } catch (e: KeeperException.NodeExistsException) {
      leaseHeldByAnother()
    } catch (e: Exception) {
      log.warn(e) { "got unexpected exception trying to acquire node for lease $name" }
    }

    return false
  }

  private fun notifyAfterAcquire() {
    listeners.forEach {
      try {
        it.afterAcquire(this)
      } catch (e: Exception) {
        log.warn(e) { "exception from afterAcquire() listener for lease $name" }
      }
    }
  }

  private fun notifyBeforeRelease() {
    listeners.forEach {
      try {
        it.beforeRelease(this)
      } catch (e: Exception) {
        log.warn(e) { "exception from beforeRelease() listener for lease $name" }
      }
    }
  }

  /** @return true if we should hold the lease per the cluster membership */
  private fun shouldHoldLease(): Boolean {
    val clusterSnapshot = manager.cluster.snapshot
    return getDesiredLeaseHolder(clusterSnapshot)?.name == clusterSnapshot.self.name &&
      clusterWeight.get() > 0
  }

  private fun getDesiredLeaseHolder(clusterSnapshot: Cluster.Snapshot): Cluster.Member? {
    return try {
      clusterSnapshot.resourceMapper[leaseResourceName]
    } catch (e: NoMembersAvailableException) {
      // no healthy members in the cluster yet
      null
    }
  }

  /** @return true if the lease node exists in zk */
  private fun checkLeaseNodeExists() =
    manager.client.value.checkExists().forPath(leaseZkPath) != null

  /** @return true if the lease data held in the zk node matches our lease data */
  private fun checkLeaseDataMatches() = leaseData.contentEquals(currentLeaseData() ?: byteArrayOf())

  /** @return the current lease data from the zk node */
  private fun currentLeaseData() = try {
    manager.client.value.data.forPath(leaseZkPath)
  } catch (e: KeeperException.NoNodeException) {
    // The lease node no longer exists
    null
  }

  /** Called when we detect that the lease is being actively held by another instance */
  private fun leaseHeldByAnother() {
    if (status != Status.NOT_HELD) {
      status = Status.NOT_HELD
      log.warn { "updating status for lease $name to ${Status.NOT_HELD}; it is held by another process" }
    } else {
      log.info { "lease $name is held by another process; skipping acquiring" }
    }
  }

  companion object {
    private val log = getLogger<ZkLease>()
  }
}
