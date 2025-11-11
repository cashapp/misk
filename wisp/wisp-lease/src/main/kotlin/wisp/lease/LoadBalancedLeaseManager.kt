package wisp.lease

/**
 * A marker interface for [LeaseManager] implementations that provide load-balanced lease
 * distribution across service instances (e.g. consistent hashing).
 *
 * Load-balanced lease managers ensure that leases are distributed across different instances
 * to spread load and prevent a single instance from acquiring all leases. This is useful for
 * scenarios where multiple leases need to be processed in parallel across a cluster.
 *
 * Implementations should ensure that:
 * - Leases are distributed fairly across instances
 * - No single instance is overwhelmed with too many leases
 * - The distribution adapts to instance availability changes
 */
interface LoadBalancedLeaseManager : LeaseManager