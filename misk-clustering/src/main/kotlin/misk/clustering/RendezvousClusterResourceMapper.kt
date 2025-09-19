package misk.clustering

import com.dynatrace.hash4j.hashing.Hashing
import com.github.benmanes.caffeine.cache.Caffeine
import java.time.Duration

/**
 * Maps cluster members to resources via rendezvous consistent hashing
 */
class RendezvousClusterResourceMapper(val members: Collection<Cluster.Member>): ClusterResourceMapper {
  // Use a cache with TTLs to reduce unbounded cache growth from the creation of ephemeral leases
  private val cache = Caffeine.newBuilder().expireAfterAccess(Duration.ofHours(1)).build<String, Cluster.Member>()

  override fun get(resourceId: String): Cluster.Member {
    var bestMember = getFromCache(resourceId)
    if (bestMember != null) {
      return bestMember
    }

    bestMember = members.maxByOrNull { hashClusterResource(it, resourceId) }

    if (bestMember == null) {
      throw NoMembersAvailableException(resourceId)
    }

    return cache.get(resourceId) { _ -> bestMember }
  }

  internal fun getFromCache(resourceId: String): Cluster.Member?  = cache.getIfPresent(resourceId)

  internal fun hashClusterResource(member: Cluster.Member, resourceId: String): Long {
    return Hashing.rapidhash3()
      .hashStream()
      .putString(member.name)
      .putString(":")
      .putString(member.ipAddress)
      .putString("###")
      .putString(resourceId)
      .asLong;
  }
}
