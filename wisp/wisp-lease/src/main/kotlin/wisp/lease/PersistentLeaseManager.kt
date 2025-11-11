package wisp.lease

/**
 * A marker interface for [LeaseManager] implementations that use persistent storage
 * to manage leases across service restarts and failures.
 *
 * Persistent lease managers store lease state in durable storage (such as databases),
 * ensuring that leases survive process restarts and can be coordinated across multiple
 * instances even in the presence of network partitions or instance failures. This is
 * useful for scenarios requiring strong consistency guarantees and lease durability.
 *
 * Implementations should ensure that:
 * - Lease state persists across service restarts
 * - Multiple instances can safely compete for leases using the storage backend
 * - Stale leases are properly expired based on storage-level timestamps
 * - The system handles storage failures gracefully
 */
interface PersistentLeaseManager : LeaseManager
