package misk.eventrouter

import com.google.common.reflect.TypeToken
import io.kubernetes.client.apis.CoreV1Api
import io.kubernetes.client.models.V1Pod
import io.kubernetes.client.util.Config
import io.kubernetes.client.util.Watch
import misk.logging.getLogger
import misk.web.WebConfig
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.ByteString
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val logger = getLogger<KubernetesClusterConnector>()

@Singleton
internal class KubernetesClusterConnector : ClusterConnector {
  @Inject @ForKubernetesWatching lateinit var executor: ExecutorService
  @Inject lateinit var config: KubernetesConfig
  @Inject lateinit var webConfig: WebConfig

  private lateinit var api: CoreV1Api
  private var hostMapping: Map<String, String> = mapOf()

  override fun joinCluster(topicPeer: TopicPeer) {
    val client = Config.defaultClient()
    client.httpClient.setReadTimeout(0, TimeUnit.MILLISECONDS)

    api = CoreV1Api(client)
    executor.execute({
      val watch = Watch.createWatch<V1Pod>(
          client,
          api.listNamespacedPodCall(config.my_pod_namespace, null, null, null, null, null, true,
              null, null),
          object : TypeToken<Watch.Response<V1Pod>>() {}.type)

      for (item in watch) {
        val name = item.`object`.metadata.name
        val podIP = item.`object`.status.podIP
        hostMapping = when (item.type) {
          "ADDED", "MODIFIED" -> {
            if (item.`object`.status.containerStatuses.first().isReady && !podIP.isNullOrBlank()) {
              hostMapping.plus(Pair(name, podIP))
            } else {
              hostMapping.minus(name)
            }
          }
          "DELETED" -> hostMapping.minus(name)
          else -> hostMapping
        }

        topicPeer.clusterChanged(ClusterSnapshot(hostMapping.keys.toList(), config.my_pod_name))
      }
    })
  }

  override fun leaveCluster(topicPeer: TopicPeer) {
    executor.shutdownNow()
  }

  override fun connectSocket(hostname: String, listener: WebSocketListener): WebSocket {
    val client = OkHttpClient.Builder()
        .build()

    val request = okhttp3.Request.Builder()
        .url("ws://${hostMapping[hostname]}:${webConfig.port}/eventrouter")
        .build()

    val webSocketListener = object : okhttp3.WebSocketListener() {
      lateinit var miskWebSocket: WebSocket

      override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
        listener.onMessage(miskWebSocket, text)
      }

      override fun onMessage(webSocket: okhttp3.WebSocket, bytes: ByteString) {
        listener.onMessage(miskWebSocket, bytes)
      }

      override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
        listener.onClosing(miskWebSocket, code, reason)
      }

      override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
        listener.onClosed(miskWebSocket, code, reason)
      }

      override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: Response?) {
        listener.onFailure(miskWebSocket, t)
      }
    }

    val okHttpWebSocket = client.newWebSocket(request, webSocketListener)
    val miskWebSocket = object : WebSocket {
      override fun queueSize(): Long {
        return okHttpWebSocket.queueSize()
      }

      override fun send(bytes: ByteString): Boolean {
        return okHttpWebSocket.send(bytes)
      }

      override fun send(text: String): Boolean {
        return okHttpWebSocket.send(text)
      }

      override fun close(code: Int, reason: String?): Boolean {
        return okHttpWebSocket.close(code, reason)
      }

      override fun cancel() {
        okHttpWebSocket.cancel()
      }
    }
    webSocketListener.miskWebSocket = miskWebSocket
    return miskWebSocket
  }
}
