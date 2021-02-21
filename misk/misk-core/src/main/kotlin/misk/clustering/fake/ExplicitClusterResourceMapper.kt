package misk.clustering.fake

import misk.clustering.Cluster
import misk.clustering.ClusterResourceMapper
import misk.clustering.NoMembersAvailableException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * An [ExplicitClusterResourceMapper] is a [ClusterResourceMapper] where the mapping
 * is explicit managed.
 */
class ExplicitClusterResourceMapper : ClusterResourceMapper {
  private val mappings = ConcurrentHashMap<String, Cluster.Member>()
  private val defaultMapping = AtomicReference<Cluster.Member>()

  fun addMapping(resourceId: String, member: Cluster.Member) {
    mappings[resourceId] = member
  }

  fun removeMapping(resourceId: String) {
    mappings.remove(resourceId)
  }

  fun setDefaultMapping(member: Cluster.Member) {
    defaultMapping.set(member)
  }

  fun clearDefaultMapping() {
    defaultMapping.set(null)
  }

  override fun get(resourceId: String): Cluster.Member {
    if (mappings.isEmpty()) {
      defaultMapping.get() ?: throw NoMembersAvailableException(resourceId)
    }
    return mappings[resourceId] ?: defaultMapping.get() ?: throw IllegalStateException(
      "no mapping for $resourceId"
    )
  }
}
