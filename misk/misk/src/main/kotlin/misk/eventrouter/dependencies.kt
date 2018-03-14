package misk.eventrouter

import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener

internal interface ClusterConnector {
  fun joinCluster(topicPeer: TopicPeer)
  fun leaveCluster(topicPeer: TopicPeer)
  fun connectSocket(hostname: String, listener: WebSocketListener): WebSocket
}

internal interface ClusterSnapshot {
  val version: Long
  val hosts: List<String>
  fun topicToHost(topic: String): String
  fun peerToHost(peer: TopicPeer): String
}

internal interface TopicPeer {
  fun acceptWebSocket(webSocket: WebSocket): WebSocketListener
  fun clusterChanged(clusterSnapshot: ClusterSnapshot)
}
