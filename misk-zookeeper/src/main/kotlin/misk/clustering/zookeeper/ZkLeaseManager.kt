package misk.clustering.zookeeper

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.ThreadFactoryBuilder
import misk.DependentService
import misk.clustering.Cluster
import misk.clustering.leasing.Lease
import misk.clustering.leasing.LeaseManager
import misk.config.AppName
import misk.logging.getLogger
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.state.ConnectionState
import org.apache.curator.framework.state.ConnectionStateListener
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.annotation.concurrent.GuardedBy
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

@Singleton
internal class ZkLeaseManager @Inject internal constructor(
  @AppName private val appName: String,
  internal val cluster: Cluster,
  private val curator: CuratorFramework
) : AbstractIdleService(), LeaseManager, DependentService, ConnectionStateListener {
  override val consumedKeys = setOf(ZookeeperModule.serviceKey)
  override val producedKeys = setOf(ZookeeperModule.leaseManagerKey)

  internal val client = lazy { curator.usingNamespace(appName.asZkNamespacePath) }
  private val executor = Executors.newCachedThreadPool(ThreadFactoryBuilder()
      .setNameFormat("lease-monitor-%d")
      .build())

  enum class State {
    NOT_STARTED,
    RUNNING,
    STOPPED
  }

  private val lock = ReentrantLock()
  @GuardedBy("lock") private var connected = false
  @GuardedBy("lock") private var state = State.NOT_STARTED
  @GuardedBy("lock") private val leases = mutableMapOf<String, ZkLease>()

  internal val isConnected get() = lock.withLock { connected }
  internal val isRunning get() = lock.withLock { state == State.RUNNING } && client.value.isRunning

  override fun startUp() {
    log.info { "starting zk lease manager" }
    lock.withLock {
      check(state == State.NOT_STARTED) { "attempting to start lease manager in $state state" }

      state = State.RUNNING
      client.value.connectionStateListenable.addListener(this)
      cluster.watch { handleClusterChange() }
    }
  }

  override fun shutDown() {
    log.info { "stopping zk lease manager" }
    lock.withLock {
      check(state == State.RUNNING) { "attempting to stop lease manager in $state state" }
      state = State.STOPPED

      leases.values.forEach { lease ->
        executor.submit { lease.releaseIfHeld(false) }
      }
    }

    executor.shutdown()
    executor.awaitTermination(15, TimeUnit.SECONDS)
  }

  override fun tryAcquireLease(name: String, ttl: Duration): Lease {
    return lock.withLock {
      check(state != State.STOPPED) { "attempting to acquire lease from $state lease manager" }
      leases.computeIfAbsent(name) { ZkLease(this, name) }
    }
  }

  private fun handleClusterChange() {
    // Reconfirm whether we should own each lease now that the cluster topology has changed
    val leasesToCheck = lock.withLock { leases.values }
    leasesToCheck.forEach { lease ->
      executor.submit {
        lease.checkHeld()
      }
    }
  }

  override fun stateChanged(client: CuratorFramework, newState: ConnectionState) {
    lock.withLock {
      connected = newState.isConnected
      if (!connected) {
        // Mark each lease as being in an UNKNOWN state
        leases.values.forEach { lease -> lease.connectionLost() }
      }
    }
  }

  companion object {
    private val log = getLogger<ZkLeaseManager>()
  }
}