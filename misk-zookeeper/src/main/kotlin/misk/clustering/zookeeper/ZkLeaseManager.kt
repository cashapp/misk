package misk.clustering.zookeeper

import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.AbstractExecutionThreadService
import misk.clustering.Cluster
import misk.clustering.weights.ClusterWeightProvider
import misk.config.AppName
import misk.tasks.RepeatedTaskQueue
import misk.tasks.Result
import misk.tasks.Status
import misk.zookeeper.SERVICES_NODE
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.state.ConnectionStateListener
import wisp.lease.AutoCloseableLease
import wisp.lease.LeaseManager
import wisp.logging.getLogger
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ZkLeaseManager] is a manager for leases backed by zookeeper. The lease manager tracks
 * the state of the application, the state of the connection to zookeeper, and the state
 * of the cluster, and forwards events to the individual leases under management as needed.
 */
@Singleton
internal class ZkLeaseManager @Inject internal constructor(
  @AppName appName: String,
  @ForZkLease private val taskQueue: RepeatedTaskQueue,
  internal val cluster: Cluster,
  @ForZkLease curator: CuratorFramework,
  private val clusterWeight: ClusterWeightProvider
) : AbstractExecutionThreadService(), LeaseManager {
  internal val leaseNamespace = "$SERVICES_NODE/${appName.asZkNamespace}/leases"
  internal val client = lazy { curator.usingNamespace(leaseNamespace) }

  private val ownerName = cluster.snapshot.self.name
  private val actionQueue = LinkedBlockingQueue<() -> Unit>()
  private val leasePollInterval: Duration = Duration.ofSeconds(1L)

  enum class State {
    NOT_STARTED,
    RUNNING,
    STOPPING,
    STOPPED
  }

  private val state = AtomicEnum.of(State.NOT_STARTED)
  private val leases = ConcurrentHashMap<String, ZkLease>()
  private val connectionListeners = mutableListOf<(Boolean) -> Unit>()

  internal val isConnected get() = client.value.isConnected
  internal val isRunning get() = state.get() == State.RUNNING && client.value.isRunning

  override fun startUp() {
    log.info { "starting zk lease manager" }

    if (!state.compareAndSet(State.NOT_STARTED, State.RUNNING)) return

    // When we are disconnected from zk, inform all of the leases that their state is now unknown
    client.value.connectionStateListenable.addListener(ConnectionStateListener { _, state ->
      actionQueue.put {
        handleConnectionStateChanged(state.isConnected)
      }
    })

    taskQueue.schedule(leasePollInterval) {
      checkAllLeases()
      Result(Status.OK, leasePollInterval)
    }
  }

  override fun triggerShutdown() {
    log.info { "stopping zk lease manager" }

    if (!state.compareAndSet(State.RUNNING, State.STOPPING)) return

    // Clear any pending actions, release any leases we currently hold, and stop running
    val fullyStopped = CountDownLatch(1)
    actionQueue.clear()
    actionQueue.put {
      leases.forEach { (_, lease) -> lease.close() }
      state.set(State.STOPPED)
      log.info { "fully stopped zk lease manager" }
      fullyStopped.countDown()
    }

    // Wait for full stop
    if (!fullyStopped.await(15, TimeUnit.SECONDS)) {
      log.warn { "could not release all leases" }
    }
  }

  override fun run() {
    while (state.get() != State.STOPPED) {
      try {
        actionQueue.take().invoke()
      } catch (th: Throwable) {
        log.error(th) { "error perform background action" }
      }
    }
  }

  override fun requestLease(name: String): ZkLease {
    return leases.computeIfAbsent(name) {
      ZkLease(ownerName, this, "$leaseNamespace/$name", clusterWeight, name)
    }
  }

  fun addConnectionListener(callback: (Boolean) -> Unit) {
    actionQueue.put {
      connectionListeners.add(callback)
      callback.invoke(isConnected)
    }
  }

  @VisibleForTesting internal fun handleConnectionStateChanged(connected: Boolean) {
    if (!connected) {
      log.info { "lost connection to zk server" }
      leases.forEach { (_, lease) -> lease.connectionLost() }
    } else {
      log.info { "connection established to zk server" }
    }

    connectionListeners.forEach { it.invoke(connected) }
  }

  @VisibleForTesting internal fun checkAllLeases() {
    if (state.get() != State.RUNNING) return

    // Reconfirm whether we should hold any of the leases that we have
    leases.values.forEach {
      if (!it.checkHeld()) {
        it.acquire()
      }
    }
  }

  companion object {
    private val log = getLogger<ZkLeaseManager>()
  }
}
