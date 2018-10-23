package misk.jdbc

import javax.persistence.PersistenceException

/**
 * Exception thrown if we use a scatter query that is too wide in the wrong context.
 *
 * Strongly consistent reads require reads from the master of a cluster. The master of a cluster is
 * a limited resource that we can't add more of. We can split shards but a wide scatter query will
 * still hit all of the shards so we can't scale if we have too many wide scatters. For this reason we do not allow wide scatters for strongly consistent reads.
 *
 * Note: For eventually consistent reads (that go to replicas) we may very well allow wide scatter
 * queries because we can tune the availability by adding more replicas. Currently we do NOT
 * differentiate between these types of reads for the detector but if you do need this it can be
 * implemented.
 */
class FullScatterException(
  message: String? = null,
  cause: Throwable? = null
) : PersistenceException(message, cause)