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
    val snapshot: Snapshot,
    val added: Set<Member> = setOf(),
    val removed: Set<Member> = setOf()
  ) {
    val hasDiffs = added.isNotEmpty() || removed.isNotEmpty()
  }

  /** [Snapshot] is a consistent moment-in-time view of the cluster state */
  data class Snapshot(
    /** @property Member The member representing this instance of the service */
    val self: Member,

    /** @property Boolean true if the current service instance is ready as perceived by the cluster manager */
    val selfReady: Boolean,

    /** @property Set<Member> of the ready members of the cluster */
    val readyMembers: Set<Cluster.Member>,

    /** @property ClusterHashRing built from the ready members of this cluster */
    val hashRing: ClusterHashRing = ClusterHashRing(readyMembers)
  ) {

    /** @property Set<Member> of the ready peers */
    val readyPeers: Set<Member> get() = readyMembers - self
  }

  /** @property Snapshot The current moment-in-time view of the cluster state */
  val snapshot: Snapshot

  /** Registers interest in cluster changes */
  fun watch(watch: ClusterWatch)
}