package wisp.lease

interface LeaseManager {
    /**
     * Registers interest in the lease with the given name. Service instances should register their
     * interest in leases as soon as they know about them; this gives the process an opportunity
     * to notify the underlying cluster system that it is interested in the lease, or to setup
     * other background tasks to acquire the lease if necessary. Note that registering interest
     * in a lease doesn't necessarily mean that this service instance will attempt to acquire
     * the lease; many clustering systems will only try to acquire leases for resources that
     * they think they should own based on some consistent hashing system
     */
    fun requestLease(name: String): Lease
}
