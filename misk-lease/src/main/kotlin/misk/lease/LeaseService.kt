package misk.lease

import com.google.common.util.concurrent.AbstractIdleService
import wisp.lease.LeaseManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeaseService @Inject constructor(
  private val leaseManager: LeaseManager
) : AbstractIdleService() {
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
