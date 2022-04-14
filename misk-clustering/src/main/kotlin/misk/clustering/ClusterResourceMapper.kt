package misk.clustering

/**
 * A [ClusterResourceMapper] maps string based resource IDs onto members of a cluster for the
 * purposes of resource ownership. The default [ClusterResourceMapper] is a [ClusterHashRing]
 * which performs a consistent hash across the cluster member, but [Cluster]s can supply their
 * own mappings if there is a mechanism specific to that cluster (or to supply a fake)
 */
interface ClusterResourceMapper {
  /**
   * @throws NoMembersAvailableException if there are no members in the cluster
   * @return The [Cluster.Member] that should own the given resource id
   */
  operator fun get(resourceId: String): Cluster.Member
}
