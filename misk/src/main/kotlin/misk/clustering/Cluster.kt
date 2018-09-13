package misk.clustering

/** A [ClusterWatch] is a callback function triggered when cluster membership changes */
typealias ClusterWatch = (Cluster.Changes) -> Unit

/**
 * A [Cluster] provides access to cluster membership for a service, allowing instances of a service
 * to monitor the state of its peers
 */
interface Cluster {
  data class Member(val name: String, val ipAddress: String)

  data class Changes(
    val current: Set<Member>,
    val added: Set<Member> = setOf(),
    val removed: Set<Member> = setOf()
  ) {
    val hasDiffs = added.isNotEmpty() || removed.isNotEmpty()
  }

  /** @property The member representing this instance of the service */
  val self: Member

  /** @property The set of peer instances to this service */
  val peers: Set<Member>

  /** Registers interest in cluster changes */
  fun watch(watch: ClusterWatch)
}