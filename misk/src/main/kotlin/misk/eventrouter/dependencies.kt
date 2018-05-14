package misk.eventrouter

import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener

internal interface ClusterConnector {
  fun joinCluster(topicPeer: TopicPeer)
  fun leaveCluster(topicPeer: TopicPeer)
  fun connectSocket(hostname: String, listener: WebSocketListener): WebSocket
}

data class ClusterSnapshot(
  val hosts: List<String>,
  val self: String
) {
  override fun toString(): String {
    val result = StringBuilder()
    result.append("[")
    for ((index, host) in hosts.withIndex()) {
      if (index > 0) result.append(",")
      if (host == self) {
        result.append("**").append(host).append("**")
      } else {
        result.append(host)
      }
    }
    result.append("]")
    return result.toString()
  }
}

internal interface ClusterMapper {
  fun topicToHost(clusterSnapshot: ClusterSnapshot, topic: String): String
}

internal interface TopicPeer {
  fun acceptWebSocket(webSocket: WebSocket): WebSocketListener
  fun clusterChanged(clusterSnapshot: ClusterSnapshot)
}
