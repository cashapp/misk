package misk.clustering.etcd

import com.coreos.jetcd.Client
import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import misk.DependentService
import misk.clustering.Cluster
import misk.clustering.leasing.Lease
import misk.clustering.leasing.LeaseManager
import misk.config.AppName
import misk.inject.keyOf
import misk.logging.getLogger
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EtcdLeaseManager @Inject internal constructor(
  @AppName appName: String,
  private val config: EtcdConfig,
  private val client: Client,
  private val cluster: Cluster
) : AbstractIdleService(), LeaseManager, DependentService {
  override val consumedKeys: Set<Key<*>> = setOf(keyOf<Cluster>())
  override val producedKeys: Set<Key<*>> = setOf(EtcdModule.leaseManagerKey)

  private val processLeaseId = AtomicLong(-1)
  private val processLeaseKeepAlive = AtomicReference<com.coreos.jetcd.Lease.KeepAliveListener>()
  private val leaseRoot = "/leases/$appName"
  private val running = AtomicBoolean(false)
  private val leases = ConcurrentHashMap<String, EtcdLease>()

  override fun tryAcquireLease(name: String, ttl: Duration): Lease {
    // We can hand out leases event if we're not running - we won't try to actually acquire
    // the lease unless we are actively running and have an etcd lease id
    return leases.computeIfAbsent(name) {
      EtcdLease(name, cluster, leaseRoot, client, processLeaseId)
    }
  }

  override fun startUp() {
    log.info { "starting etcd lease manager" }

    check(running.compareAndSet(false, true)) { "attempting to start a running lease manager" }

    // Acquire an etcd lease and keep it alive so long as we are running.
    val grantLeaseResponse = client.leaseClient.grant(config.session_timeout_ms).get()
    val keepAliveListener = client.leaseClient.keepAlive(grantLeaseResponse.id)

    processLeaseId.set(grantLeaseResponse.id)
    processLeaseKeepAlive.set(keepAliveListener)
  }

  override fun shutDown() {
    log.info { "shutting down etcd lease manager" }

    check(running.compareAndSet(true, false)) {
      "attempting to shutdown a non-running lease manager"
    }

    processLeaseId.set(-1)
    processLeaseKeepAlive.get()?.close()
    processLeaseKeepAlive.set(null)
  }

  companion object {
    private val log = getLogger<EtcdLeaseManager>()
  }

}