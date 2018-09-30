package misk.hash

import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing

/** A [ConsistentHashRing] maps strings to values based on a consistent hash */
class ConsistentHashRing<T>(
  members: Map<String, T>,
  private val hashFn: HashFunction = Hashing.murmur3_32(),
  private val vnodesCount: Int = 16
) {
  private val vnodes: IntArray
  private val vnodesToMembers: Map<Int, T>

  init {
    val vnodesBuilder = mutableListOf<Int>()
    val vnodesToMembers = mutableMapOf<Int, T>()

    // Hash each member up to the vnode count to occupy slots in the ring. To resolve
    // a resource, we find the vnode that is closest to the resources hash, then find
    // the member corresponding to that vnode
    members.forEach { (memberKey, member) ->
      (0 until vnodesCount).forEach { replica ->
        val vnodeHash = hashFn.hashBytes("$memberKey $replica".toByteArray()).asInt()
        vnodesBuilder.add(vnodeHash)
        vnodesToMembers[vnodeHash] = member
      }
    }

    this.vnodesToMembers = vnodesToMembers.toMap()
    vnodes = vnodesBuilder.sorted().toIntArray()
  }

  /** @return The value onto which the given id maps */
  operator fun get(resourceId: String): T {
    check(vnodesToMembers.isNotEmpty()) { "no members available for $resourceId" }

    val resourceHash = hashFn.hashBytes(resourceId.toByteArray()).asInt()
    val vnode = vnodes.first { it >= resourceHash }
    return vnodesToMembers[vnode] ?: throw IllegalStateException(
        "no member corresponding to vnode hash $vnode")
  }

}