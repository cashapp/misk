package misk.cron

import jakarta.inject.Inject
import wisp.lease.LeaseManager

interface CronCoordinator {
  fun shouldRunTask(taskName: String): Boolean
}

class SingleLeaseCronCoordinator @Inject constructor(
  private val leaseManager: LeaseManager
) : CronCoordinator {
  override fun shouldRunTask(taskName: String): Boolean {
    val lease = leaseManager.requestLease(CRON_CLUSTER_LEASE_NAME)
    return lease.checkHeld() || lease.acquire()
  }

  companion object {
    const val CRON_CLUSTER_LEASE_NAME = "misk.cron.lease"
  }
}

class MultipleLeaseCronCoordinator @Inject constructor(
  private val leaseManager: LeaseManager
) : CronCoordinator {
  override fun shouldRunTask(taskName: String): Boolean {
    val taskLease = leaseManager.requestLease("misk.cron.task.$taskName")
    return taskLease.checkHeld() || taskLease.acquire()
  }
}