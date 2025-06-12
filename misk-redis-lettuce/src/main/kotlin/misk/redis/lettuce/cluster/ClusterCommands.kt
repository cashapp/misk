package misk.redis.lettuce.cluster

import java.util.concurrent.CompletionStage

/**
 * Represents information about a Redis cluster's current state and configuration.
 *
 * @property state The current state of the cluster
 * @property slotsAssigned The total number of slots that are assigned in the cluster
 * @property slotsOk The number of slots that are in a healthy state
 * @property slotsPfail The number of slots that are in a probable failure state
 * @property slotsFail The number of slots that are in a failed state
 * @property knownNodes The total number of nodes known to the cluster
 * @property size The size of the cluster (number of primary nodes)
 */
data class ClusterInfo(
  val state: State,
  val slotsAssigned: Int,
  val slotsOk: Int,
  val slotsPfail: Int,
  val slotsFail: Int,
  val knownNodes: Int,
  val size: Int
) {
  /**
   * Defines possible states for a Redis cluster.
   */
  enum class State {
    Undefined,
    Ok,
    Fail;
  }

  /**
   * Builder class for creating [ClusterInfo] instances.
   */
  class Builder {
    var state: State = State.Undefined
    var slotsAssigned: Int = 0
    var slotsOk: Int = 0
    var slotsPfail: Int = 0
    var slotsFail: Int = 0
    var knownNodes: Int = 0
    var size: Int = 0
    fun build() = ClusterInfo(
      state = state,
      slotsAssigned = slotsAssigned,
      slotsOk = slotsOk,
      slotsPfail = slotsPfail,
      slotsFail = slotsFail,
      knownNodes = knownNodes,
      size = size,
    )
  }
}

/**
 * Creates a [ClusterInfo] instance using the provided builder block.
 *
 * @param builder Lambda with receiver that configures a [ClusterInfo.Builder]
 * @return A new [ClusterInfo] instance
 */
inline fun clusterInfo(builder: ClusterInfo.Builder.() -> Unit): ClusterInfo =
  ClusterInfo.Builder().apply(builder).build()

/**
 * Parses a Redis CLUSTER INFO command response string into a [ClusterInfo] instance.
 *
 * @return [ClusterInfo] containing the parsed cluster information
 */
fun String.toClusterInfo() = clusterInfo {
  lineSequence().forEach { line ->
    val parts = line.split(":")
    if (parts.size == 2) {
      when (parts[0]) {
        "cluster_state" -> state = when{
          parts[1] == "ok" -> ClusterInfo.State.Ok
          parts[1] == "fail" -> ClusterInfo.State.Fail
          else -> ClusterInfo.State.Undefined
        }
        "cluster_slots_assigned" -> slotsAssigned = parts[1].toInt()
        "cluster_slots_ok" -> slotsOk = parts[1].toInt()
        "cluster_slots_pfail" -> slotsPfail = parts[1].toInt()
        "cluster_slots_fail" -> slotsFail = parts[1].toInt()
        "cluster_known_nodes" -> knownNodes = parts[1].toInt()
        "cluster_size" -> size = parts[1].toInt()
      }
    }
  }
}

/**
 * Extension function to parse a [CompletionStage] containing a cluster info string into a [ClusterInfo].
 *
 * @return [CompletionStage] containing the parsed [ClusterInfo]
 */
fun CompletionStage<String>.toClusterInfo(): CompletionStage<ClusterInfo> = thenApply { it.toClusterInfo() }

/**
 * Represents a node in a Redis cluster with its configuration and state.
 *
 * @property id The unique identifier of the node
 * @property host The hostname or IP address of the node
 * @property port The port number the node is listening on
 * @property clusterBusPort The port used for cluster bus communication (null if not specified)
 * @property role The role of the node (Primary or Replica)
 * @property flags List of flags associated with the node
 * @property masterId The ID of the primary node if this is a replica, null otherwise
 * @property pingSent The timestamp of the last ping sent
 * @property pongRecv The timestamp of the last pong received
 * @property configEpoch The configuration version number
 * @property connectionState The current connection state of the node
 * @property slots List of slot ranges assigned to this node
 */
data class ClusterNode(
  val id: String,
  val host: String,
  val port: Int,
  val clusterBusPort: Int?,
  val role: Role,
  val flags: List<String>,
  val masterId: String?,
  val pingSent: Long,
  val pongRecv: Long,
  val configEpoch: Long,
  val connectionState: ConnectionState,
  val slots: List<String>
) {
  /**
   * Defines possible roles for a cluster node.
   */
  enum class Role { Undefined, Primary, Replica }

  /**
   * Defines possible connection states for a cluster node.
   */
  enum class ConnectionState { Undefined, Connected, Fail, Handshake, NoAddr }

  /**
   * Indicates if the node is currently connected.
   */
  val isConnected: Boolean get() = connectionState == ConnectionState.Connected

  /**
   * Indicates if the node is in a failing state.
   */
  val isFailing: Boolean get() = !isConnected

  /**
   * Indicates if the node has any slots assigned to it.
   */
  val hasSlots: Boolean get() = slots.isNotEmpty()

  /**
   * Builder class for creating [ClusterNode] instances.
   */
  class Builder {
    var id: String = ""
    var host: String = ""
    var port: Int = 0
    var clusterBusPort: Int? = null
    var role: Role = Role.Undefined
    var flags: List<String> = emptyList()
    var masterId: String? = null
    var pingSent: Long = 0
    var pongRecv: Long = 0
    var configEpoch: Long = 0
    var connectionState: ConnectionState = ConnectionState.Undefined
    var slots: List<String> = emptyList()

    fun build() = ClusterNode(
      id = id,
      host = host,
      port = port,
      clusterBusPort = clusterBusPort,
      role = role,
      flags = flags,
      masterId = masterId,
      pingSent = pingSent,
      pongRecv = pongRecv,
      configEpoch = configEpoch,
      connectionState = connectionState,
      slots = slots,
    )
  }
}

/**
 * Creates a [ClusterNode] instance using the provided builder block.
 *
 * @param builder Lambda with receiver that configures a [ClusterNode.Builder]
 * @return A new [ClusterNode] instance
 */
inline fun clusterNode(builder: ClusterNode.Builder.() -> Unit) =
  ClusterNode.Builder().apply(builder).build()

/**
 * Parses a Redis CLUSTER NODES command response string into a list of [ClusterNode] instances.
 *
 * The response string is expected to contain one node per line with space-separated fields
 * in the format specified by Redis CLUSTER NODES command.
 *
 * @return List of [ClusterNode] instances representing the cluster nodes
 */
fun String.toClusterNodes(): List<ClusterNode> =
  buildList {
    lineSequence()
      .filter { it.isNotBlank() }
      .forEach { line ->
        val parts = line.split(" ")
        val (id, address, flagsRaw, masterId) = parts.subList(0, 4)
        val (pingSent, pongRecv, configEpoch, state) = parts.subList(4, 8)
        val (host, portWithBus) = address.split(":")
        val (port, busPort) = if (portWithBus.contains("@")) {
          val (p, b) = portWithBus.split("@")
          p.toInt() to b.toInt()
        } else {
          portWithBus.toInt() to null
        }
        add(clusterNode {
          this.id = id
          this.host = host
          this.port = port
          this.clusterBusPort = busPort
          this.role = if ("master" in flagsRaw) ClusterNode.Role.Primary else ClusterNode.Role.Replica
          this.flags = flagsRaw.split(",")
          this.masterId = if (masterId == "-") null else masterId
          this.pingSent = pingSent.toLong()
          this.pongRecv = pongRecv.toLong()
          this.configEpoch = configEpoch.toLong()
          this.connectionState = when {
            state.contains("connected") -> ClusterNode.ConnectionState.Connected
            state.contains("fail") || state.contains("fail?") -> ClusterNode.ConnectionState.Fail
            state.contains("handshake") -> ClusterNode.ConnectionState.Handshake
            state.contains("noaddr") -> ClusterNode.ConnectionState.NoAddr
            else -> ClusterNode.ConnectionState.Undefined
          }
          this.slots = if (parts.size > 8) parts.subList(8, parts.size) else emptyList()
        })
      }
  }

/**
 * Extension function to parse a [CompletionStage] containing a cluster nodes string into a list of [ClusterNode] instances.
 *
 * @return [CompletionStage] containing the parsed list of [ClusterNode] instances
 */
fun CompletionStage<String>.toClusterNodes(): CompletionStage<List<ClusterNode>> =
  thenApply { it.toClusterNodes() }
