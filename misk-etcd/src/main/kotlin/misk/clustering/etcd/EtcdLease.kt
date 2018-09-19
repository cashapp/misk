package misk.clustering.etcd

import com.coreos.jetcd.Client
import com.coreos.jetcd.data.ByteSequence
import com.coreos.jetcd.op.Cmp
import com.coreos.jetcd.op.Op
import com.coreos.jetcd.options.DeleteOption
import com.coreos.jetcd.options.GetOption
import com.coreos.jetcd.options.PutOption
import misk.clustering.Cluster
import misk.clustering.leasing.Lease
import misk.logging.getLogger
import java.util.concurrent.atomic.AtomicLong
import javax.annotation.concurrent.GuardedBy

internal class EtcdLease(
  override val name: String,
  private val cluster: Cluster,
  leaseRoot: String,
  private val client: Client,
  private val processLeaseId: AtomicLong
) : Lease {
  private val leasePath = "$leaseRoot/$name"
  private val leaseKey = ByteSequence.fromCharSequence(leasePath)
  private val leaseData = ByteSequence.fromCharSequence(cluster.snapshot.self.name)

  @GuardedBy("this") private var heldBySelf: Boolean = false

  override fun checkHeld(): Boolean {
    // If we don't have a process-wide etcd lease (meaning the lease manager either
    // hasn't started or has stopped) then we definitely don't own any application leases
    if (processLeaseId.get() == -1L) {
      return false
    }

    // Check if we should own the application lease according to the cluster hash
    val clusterSnapshot = cluster.snapshot
    val desiredLeaseHolder = clusterSnapshot.resourceMapper[leasePath]
    if (desiredLeaseHolder.name != clusterSnapshot.self.name) {
      // We shouldn't own the lease, so release it if we do
      releaseIfHeld()
      return false
    }

    return tryAcquireLease()
  }

  private fun tryAcquireLease(): Boolean {
    try {
      synchronized(this) {
        if (heldBySelf) {
          // We should hold it, and we do hold it
          return true
        }

        // We should hold it, but we don't hold it. Attempt to acquire it, failing if someone
        // else holds it
        val response = client.kvClient.txn()
            .If(Cmp(leaseKey, Cmp.Op.EQUAL, CmpTargets.version(0L)))
            .Then(Op.put(leaseKey, leaseData, PutOption.newBuilder()
                .withLeaseId(processLeaseId.get())
                .build()))
            .Else(Op.get(leaseKey, GetOption.DEFAULT)).commit().get()

        if (!response.isSucceeded) {
          val existingLeaseHolder =
              response.getResponses.first().kvs.first().value.toString(Charsets.UTF_8)
          log.warn { "could not acquire lease $name - currently held by $existingLeaseHolder" }
          return false
        }

        log.info { "acquired lease $name" }
        heldBySelf = true
        return true
      }
    } catch (e: Exception) {
      log.error(e) { "could not acquire lease $name" }
      return false
    }
  }

  private fun releaseIfHeld() {
    try {
      synchronized(this) {
        if (!heldBySelf) {
          return
        }

        heldBySelf = false

        val response = client.kvClient.txn()
            .If(Cmp(leaseKey, Cmp.Op.EQUAL, CmpTargets.value(leaseData)))
            .Then(Op.delete(leaseKey, DeleteOption.DEFAULT))
            .Else(Op.get(leaseKey, GetOption.DEFAULT)).commit().get()
        if (response.isSucceeded) {
          log.info { "released lease $name" }
        } else {
          val existingLeaseHolder =
              response.getResponses.first().kvs.first().value.toString(Charsets.UTF_8)
          log.warn { "could not release lease $name - currently held by $existingLeaseHolder" }
        }
      }
    } catch (e: Exception) {
      log.warn(e) { "could not release lease $name" }
    }
  }

  companion object {
    private val log = getLogger<EtcdLease>()
  }
}