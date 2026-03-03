package misk.eventrouter

import misk.clustering.kubernetes.KubernetesConfig
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class LocalClusterConnector @Inject constructor() : ClusterConnector {
  @Inject lateinit var kubernetesConfig: KubernetesConfig

  override fun joinCluster(topicPeer: TopicPeer) {
    topicPeer.clusterChanged(
        ClusterSnapshot(listOf(kubernetesConfig.my_pod_name),
            kubernetesConfig.my_pod_name))
  }

  override fun leaveCluster(topicPeer: TopicPeer) {
  }

  override fun connectSocket(hostname: String, listener: WebSocketListener): WebSocket {
    throw IllegalStateException("In a single machine system there should be no network requests")
  }
}
