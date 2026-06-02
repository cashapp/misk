package misk.lease

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import wisp.lease.LeaseManager

@Singleton
class LeaseService @Inject constructor(private val leaseManager: LeaseManager) : AbstractIdleService() {
  override fun startUp() {
    val lease = leaseManager.requestLease(CHECK_SERVICE_LEASE)
    lease.acquire()
    lease.release()
  }

  override fun shutDown() {
    leaseManager.releaseAll()
  }

  companion object {
    private const val CHECK_SERVICE_LEASE = "check-service-lease"
  }
}
