package misk.lease.mysql

import misk.annotation.ExperimentalMiskApi

@ExperimentalMiskApi
data class SqlLeaseConfig @JvmOverloads constructor(
  val leaseDurationInSec: Long = 300L
)
