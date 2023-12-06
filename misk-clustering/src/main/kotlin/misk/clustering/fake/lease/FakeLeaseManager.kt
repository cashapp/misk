package misk.clustering.fake.lease

import wisp.lease.FakeLeaseManager
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * A [FakeLeaseManager] provides explicit control over leases for the purposes of testing. By
 * default a lease is considered held, but it can be explicitly marked as not held if desired
 */
@Singleton
class FakeLeaseManager @Inject constructor() : FakeLeaseManager()
