package misk.clustering

import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import misk.hash.ConsistentHashRing

/** A [ClusterHashRing] maps resources to cluster members based on a consistent hash */
class ClusterHashRing(
  members: Collection<Cluster.Member>,
  hashFn: HashFunction = Hashing.murmur3_32(),
  vnodesCount: Int = 16
) : ClusterResourceMapper {
  private val consistentHashRing = ConsistentHashRing(
      members = members.map { it.name to it }.toMap(),
      hashFn = hashFn,
      vnodesCount = vnodesCount
  )

  /** @return The [Cluster.Member] that should own the given resource id */
  override fun get(resourceId: String): Cluster.Member = consistentHashRing[resourceId]
}