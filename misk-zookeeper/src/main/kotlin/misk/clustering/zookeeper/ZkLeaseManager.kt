package misk.clustering.zookeeper

import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.AbstractExecutionThreadService
import com.google.common.util.concurrent.ThreadFactoryBuilder
import misk.DependentService
import misk.clustering.Cluster
import misk.clustering.lease.LeaseManager
import misk.config.AppName
import misk.inject.keyOf
import misk.logging.getLogger
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.state.ConnectionStateListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
  internal val cluster: Cluster,
  curator: CuratorFramework
) : AbstractExecutionThreadService(), LeaseManager, DependentService {
  override val consumedKeys = setOf(ZookeeperModule.serviceKey, keyOf<Cluster>())
  override val producedKeys = setOf(ZookeeperModule.leaseManagerKey)

  internal val leaseNamespace = "leases/${appName.asZkNamespace}"
  internal val client = lazy { curator.usingNamespace(leaseNamespace) }

  private val ownerName = cluster.snapshot.self.name
  private val actionQueue = LinkedBlockingQueue<() -> Unit>()
  private val leaseCheckExecutor = Executors.newCachedThreadPool(ThreadFactoryBuilder()
      .setNameFormat("lease-monitor-%d")
      .build())

  enum class State {
    NOT_STARTED,
    RUNNING,
    STOPPING,
    STOPPED
  }

  private val state = AtomicEnum.of(State.NOT_STARTED)
  private val connected = AtomicBoolean()
  private val leases = ConcurrentHashMap<String, ZkLease>()
  private val connectionListeners = mutableListOf<(Boolean) -> Unit>()

  internal val isConnected get() = connected.get()
  internal val isRunning get() = state.get() == State.RUNNING && client.value.isRunning

  override fun startUp() {
    log.info { "starting zk lease manager" }

    if (!state.compareAndSet(State.NOT_STARTED, State.RUNNING)) return
    connected.set(false)

    // When a cluster change occurs, recheck each lease to see if it should still be owned
    cluster.watch {
      actionQueue.put {
        handleClusterChange()
      }
    }

    // When we are disconnected from zk, inform all of the leases that their state is now unknown
    client.value.connectionStateListenable.addListener(ConnectionStateListener { _, state ->
      actionQueue.put {
        handleConnectionStateChanged(state.isConnected)
      }
    })
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

    // Empty out any pending lease checks
    leaseCheckExecutor.shutdownNow()

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
      ZkLease(ownerName, this, "$leaseNamespace/$name", name)
    }
  }

  fun addConnectionListener(callback: (Boolean) -> Unit) {
    actionQueue.put {
      connectionListeners.add(callback)
      callback.invoke(connected.get())
    }
  }

  @VisibleForTesting internal fun handleConnectionStateChanged(connected: Boolean) {
    this.connected.set(connected)
    if (!connected) {
      leases.forEach { (_, lease) -> lease.connectionLost() }
    }

    connectionListeners.forEach { it.invoke(connected) }
  }

  @VisibleForTesting internal fun handleClusterChange() {
    if (state.get() != State.RUNNING) return

    // Reconfirm whether we should hold any of the leases that we have
    leases.values.forEach { lease ->
      leaseCheckExecutor.submit {
        lease.checkHeld()
      }
    }
  }

  companion object {
    private val log = getLogger<ZkLeaseManager>()
  }
}