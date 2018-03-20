package misk.eventrouter

import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener

internal interface ClusterConnector {
  fun joinCluster(topicPeer: TopicPeer)
  fun leaveCluster(topicPeer: TopicPeer)
  fun connectSocket(hostname: String, listener: WebSocketListener): WebSocket
}

data class ClusterSnapshot(
  val version: Long,
  val hosts: List<String>,
  val self: String
)

internal interface ClusterMapper {
  fun topicToHost(clusterSnapshot: ClusterSnapshot, topic: String): String
}

internal interface TopicPeer {
  fun acceptWebSocket(webSocket: WebSocket): WebSocketListener
  fun clusterChanged(clusterSnapshot: ClusterSnapshot)
}
