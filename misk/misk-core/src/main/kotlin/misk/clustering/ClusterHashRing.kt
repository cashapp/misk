package misk.clustering

import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import java.util.Arrays
import java.util.Objects

/** A [ClusterHashRing] maps resources to cluster members based on a consistent hash */
class ClusterHashRing(
  members: Collection<Cluster.Member>,
  private val hashFn: HashFunction = Hashing.murmur3_32(),
  private val vnodesCount: Int = 16
) : ClusterResourceMapper {
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
  override fun get(resourceId: String): Cluster.Member {
    if (vnodesToMembers.isEmpty()) {
      throw NoMembersAvailableException(resourceId)
    }

    val resourceHash = hashFn.hashBytes(resourceId.toByteArray()).asInt()
    val vnode = vnodes.firstOrNull { it >= resourceHash } ?: vnodes[0]
    return vnodesToMembers[vnode] ?: throw IllegalStateException(
      "no member corresponding to vnode hash $vnode"
    )
  }

  override fun equals(other: Any?): Boolean {
    val otherRing = other as? ClusterHashRing ?: return false
    return vnodesCount == otherRing.vnodesCount &&
      vnodes.contentEquals(otherRing.vnodes) &&
      vnodesToMembers == other.vnodesToMembers
  }

  override fun hashCode(): Int {
    return Objects.hash(vnodesCount, vnodesToMembers, Arrays.hashCode(vnodes))
  }
}
