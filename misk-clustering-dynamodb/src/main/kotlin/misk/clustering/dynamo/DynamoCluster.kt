package misk.clustering.dynamo

import misk.clustering.Cluster
import misk.clustering.Cluster.Member
import misk.clustering.Cluster.Snapshot
import misk.clustering.ClusterHashRing
import misk.clustering.ClusterWatch

@Deprecated("Use DefaultCluster")
class DynamoCluster(self: String) : Cluster {
  private val memberSelf = Member(self, "invalid-ip")
  private var cachedSnapshot = Snapshot(
    self = memberSelf,
    readyMembers = setOf(),
    resourceMapper = ClusterHashRing(setOf())
  )

  override val snapshot: Snapshot
    get() = cachedSnapshot

  override fun watch(watch: ClusterWatch) {
    error("We are no longer supporting this operation.")
  }

  @Synchronized
  fun update(members: Set<Member>) {
    // Nothing to do!
    if (cachedSnapshot.readyMembers == members) {
      return
    }

    cachedSnapshot = cachedSnapshot.copy(
      readyMembers = members,
      resourceMapper = ClusterHashRing(members)
    )
  }
}
