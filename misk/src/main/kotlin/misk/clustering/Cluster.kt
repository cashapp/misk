package misk.clustering

import misk.clustering.partition.ClusterHashRing
import misk.clustering.partition.Partitioner

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
    /** The member representing this instance of the service */
    val self: Member,

    /** All of the members of the cluster that are up and reporting as ready to handle traffic */
    val readyMembers: Set<Cluster.Member>,

    /** true if the current service instance is ready as perceived by the cluster manager */
    val selfReady: Boolean = readyMembers.any { it.name == self.name },

    /** A [Partitioner] built from the ready members of this cluster */
    val partitioner: Partitioner
  ) {
    /** The of the ready peers; basically all of the ready cluster members except sel */
    val readyPeers: Set<Member> = readyMembers - self
  }

  /** The current moment-in-time view of the cluster state */
  val snapshot: Snapshot

  /** Registers interest in cluster changes */
  fun watch(watch: ClusterWatch)

  /** @return A new [Partitioner] for the given set of ready members */
  fun newPartitioner(readyMembers: Set<Cluster.Member>): Partitioner =
      ClusterHashRing(readyMembers)
}
