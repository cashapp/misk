package misk.clustering.partition

import misk.clustering.Cluster

/**
 * A [Partitioner] maps string based resource IDs onto members of a cluster for the
 * purposes of resource ownership. The default [Partitioner] is a [ClusterHashRing]
 * which performs a consistent hash across the cluster member, but [Cluster]s can supply their
 * own mappings if there is a mechanism specific to that cluster (or to supply a fake)
 */
interface Partitioner {
  /**
   * @throws NoMembersAvailableException if there are no members in the cluster
   * @return The [Cluster.Member] that should own the given resource id
   */
  operator fun get(resourceId: String): Cluster.Member
}
