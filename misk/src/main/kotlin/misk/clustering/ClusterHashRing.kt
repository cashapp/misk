package misk.clustering

import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing

/** A [ClusterHashRing] maps resources to cluster members based on a consistent hash */
class ClusterHashRing(
  members: Collection<Cluster.Member>,
  private val hashFn: HashFunction = Hashing.murmur3_32(),
  private val vnodesCount: Int = 16
) {
  private val vnodes: IntArray
  private val vnodesToMembers: Map<Int, Cluster.Member>

  init {
    val vnodesBuilder = mutableListOf<Int>()
    val vnodesToMembers = mutableMapOf<Int, Cluster.Member>()

    // Hash each member up to the vnode count to occupy slots in the ring. To resolve
    // a resource, we find the vnode that is closest to the resources hash, then find
    // the member corresponding to that vnode
    members.forEach { member ->
      (0 until vnodesCount).forEach { replica ->
        val vnodeHash = hashFn.hashBytes("${member.name} $replica".toByteArray()).asInt()
        vnodesBuilder.add(vnodeHash)
        vnodesToMembers[vnodeHash] = member
      }
    }

    this.vnodesToMembers = vnodesToMembers.toMap()
    vnodes = vnodesBuilder.sorted().toIntArray()
  }

  /** @return The [Cluster.Member] that should own the given resource id */
  fun mapResourceToMember(resourceId: String): Cluster.Member {
    check(vnodesToMembers.isNotEmpty()) { "no members available for $resourceId" }

    val resourceHash = hashFn.hashBytes(resourceId.toByteArray()).asInt()
    val vnode = vnodes.first { it >= resourceHash }
    return vnodesToMembers[vnode] ?: throw IllegalStateException(
        "no member corresponding to vnode hash $vnode")
  }

  override fun equals(other: Any?): Boolean {
    val otherRing = other as? ClusterHashRing ?: return false
    return vnodesCount == otherRing.vnodesCount &&
        vnodes.contentEquals(otherRing.vnodes) &&
        vnodesToMembers == other.vnodesToMembers
  }
}