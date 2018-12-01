package misk.clustering.lease

/**
 * A [Lease] is a cluster-wide time-based lock on a given resource. Leases are retrieved via
 * [LeaseManager.requestLease]. A ready service instance will automatically attempt to acquire
 * leases that it thinks it should own (typically based on the resource being leased consistently
 * hashing to the service instance), and will continue to maintain the lease for as long as it is
 * still ready. Leases should be released if the service transitions into not ready; to avoid
 * flapping, service lease owners may want to delay releasing leases until they've been not ready
 * for a particular amount of time.
 */
interface Lease {
  /** @property String the name of the resource being leased */
  val name: String

  /**
   * @return true if the lease is owned by this process instance. This may involve remote calls,
   * so it is marked as a function rather than a property to make the potential expense clearer
   */
  fun checkHeld(): Boolean
}
