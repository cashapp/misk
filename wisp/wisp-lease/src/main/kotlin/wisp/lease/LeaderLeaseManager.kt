package wisp.lease

/**
 * A marker interface for [LeaseManager] implementations that provide leader election
 * functionality across service instances (e.g. first-come first-serve, quorum).
 *
 * Leader lease managers ensure that only one instance in a cluster holds a particular lease
 * at a time, making that instance the "leader" for coordinating work or making decisions.
 * This is useful for scenarios where a single coordinator is needed to prevent conflicts
 * or duplicate processing.
 *
 * Implementations should ensure that:
 * - Only one instance holds the leader lease at any given time
 * - Leadership transfers gracefully when the current leader fails or releases the lease
 * - The system can detect and handle split-brain scenarios
 */
interface LeaderLeaseManager : LeaseManager
