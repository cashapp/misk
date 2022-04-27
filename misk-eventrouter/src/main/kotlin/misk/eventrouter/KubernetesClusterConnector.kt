package misk.eventrouter

import com.google.common.reflect.TypeToken
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.util.Config
import io.kubernetes.client.util.Watch
import misk.clustering.kubernetes.KubernetesConfig
import misk.healthchecks.HealthStatus
import misk.web.WebConfig
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.ByteString
import wisp.logging.getLogger
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val logger = getLogger<KubernetesClusterConnector>()

@Singleton
internal class KubernetesClusterConnector @Inject constructor() : ClusterConnector {
  @Inject @ForKubernetesWatching lateinit var executor: ExecutorService
  @Inject lateinit var config: KubernetesConfig
  @Inject lateinit var webConfig: WebConfig
  @Inject lateinit var clock: Clock

  private var hostMapping = mapOf<String, String>()
  private var lastReceivedMessage: Instant? = null

  fun healthStatus(): HealthStatus {
    if (lastReceivedMessage == null) {
      return HealthStatus.unhealthy("k8s: I've never received a message")
    }
    val sinceLastReceived = Duration.between(lastReceivedMessage, clock.instant()).seconds
    if (sinceLastReceived > config.kubernetes_read_timeout + config.kubernetes_connect_timeout) {
      return HealthStatus.unhealthy(
        "k8s: I haven't received an update in $sinceLastReceived seconds."
      )
    }
    return HealthStatus.healthy(
      "k8s: I received a message $sinceLastReceived seconds ago."
    )
  }

  private fun subscribeToKubernetes(client: ApiClient, api: CoreV1Api, topicPeer: TopicPeer) {
    val watch = Watch.createWatch<V1Pod>(
      client,
      api.listNamespacedPodCall(
        config.my_pod_namespace, // namespace
        null, // pretty
        false, // allowWatchBookmarks
        null, // _continue
        null, // fieldSelector
        null, // labelSelector
        null, // limit
        null, // resourceVersion
        null, // resourceVersionMatch
        null, // timeoutSeconds
        true, // watch
        null, // _callback
      ),
      object : TypeToken<Watch.Response<V1Pod>>() {}.type
    )

    for (item in watch) {
      lastReceivedMessage = clock.instant()
      val name = item.`object`.metadata!!.name!!
      val podIP = item.`object`.status!!.podIP
      hostMapping = when (item.type) {
        "ADDED", "MODIFIED" -> {
          val isReady = item.`object`!!.status!!.containerStatuses?.first()?.ready ?: false
          if (isReady && !podIP.isNullOrBlank()) {
            hostMapping.plus(Pair(name, podIP))
          } else {
            hostMapping.minus(name)
          }
        }
        "DELETED" -> hostMapping.minus(name)
        else -> hostMapping
      }

      if (hostMapping.isNotEmpty()) {
        topicPeer.clusterChanged(
          ClusterSnapshot(hostMapping.keys.toList(), config.my_pod_name)
        )
      }
    }
  }

  override fun joinCluster(topicPeer: TopicPeer) {
    val client = Config.defaultClient()
    client.httpClient = client.httpClient.newBuilder()
      .readTimeout(config.kubernetes_watch_read_timeout, TimeUnit.SECONDS)
      .connectTimeout(config.kubernetes_connect_timeout, TimeUnit.SECONDS)
      .build()
    val api = CoreV1Api(client)

    executor.execute({
      while (true) {
        try {
          subscribeToKubernetes(client, api, topicPeer)
        } catch (e: RuntimeException) {
          // This should be catching an IOException, unfortunately the kubernetes java
          // client catches the IOException and throws a RuntimeException instead.
        }
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
