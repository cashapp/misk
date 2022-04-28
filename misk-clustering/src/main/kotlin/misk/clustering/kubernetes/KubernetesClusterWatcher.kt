package misk.clustering.kubernetes

import com.google.common.base.Stopwatch
import com.google.common.reflect.TypeToken
import com.google.common.util.concurrent.AbstractIdleService
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.util.Config
import io.kubernetes.client.util.Watch
import misk.backoff.ExponentialBackoff
import misk.clustering.Cluster
import misk.clustering.DefaultCluster
import misk.clustering.kubernetes.KubernetesClusterWatcher.Companion.CHANGE_TYPE_ADDED
import misk.clustering.kubernetes.KubernetesClusterWatcher.Companion.CHANGE_TYPE_DELETED
import misk.clustering.kubernetes.KubernetesClusterWatcher.Companion.CHANGE_TYPE_MODIFIED
import wisp.logging.getLogger
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/**
 * [KubernetesClusterWatcher] watches for changes in the Kubernetes namespace to which this
 * service belongs, and sends watch events to the corresponding [DefaultCluster]
 * which determines changes to the cluster and forwards them to the application. We separate
 * cluster watching from managing cluster membership to allow each to be tested in isolation
 */
@Singleton
internal class KubernetesClusterWatcher @Inject internal constructor(
  private val cluster: DefaultCluster,
  private val config: KubernetesConfig
) : AbstractIdleService() {
  private val running = AtomicBoolean(false)
  private val watchFailedTimer = Stopwatch.createUnstarted()

  override fun startUp() {
    log.info { "starting k8s cluster watch" }
    running.set(true)
    thread(name = "k8s-cluster-watch") {
      watchCluster()
    }
  }

  override fun shutDown() {
    log.info { "stopping k8s cluster watch" }
    running.set(false)
  }

  private fun watchCluster() {
    val client = Config.defaultClient()
    client.httpClient = client.httpClient.newBuilder()
      .readTimeout(config.kubernetes_watch_read_timeout, TimeUnit.SECONDS)
      .connectTimeout(config.kubernetes_connect_timeout, TimeUnit.SECONDS)
      .build()

    val api = CoreV1Api(client)
    val connectBackoff = ExponentialBackoff(Duration.ofMillis(100), Duration.ofSeconds(5))

    while (running.get()) {
      try {
        log.debug { "preparing watch for namespace ${config.my_pod_namespace}" }
        val watch = Watch.createWatch<V1Pod>(
          client,
          api.listNamespacedPodCall(
            config.my_pod_namespace, // namespace
            null, // pretty,
            false, // allowWatchBookmarks
            null, // _continue
            null, // fieldSelector
            config.clustering_pod_label_selector, // labelSelector
            null, // limit
            null, // resourceVersion
            null, // resourceVersionMatch
            null, // timeoutSeconds
            true, // watch
            null, // _callback
          ),
          podType
        )
        // createWatch() throws an exception if the long poll fails. If we get here, it means
        // the API client received a success response code from the server and the watch is in
        // progress.
        watchFailedTimer.reset()

        watch.use {
          for (response in watch) {
            log.debug { "received watch in namespace ${config.my_pod_namespace}" }
            connectBackoff.reset()
            if (!running.get()) {
              watch.close()
              return
            }

            response.applyTo(cluster)
          }
        }
      } catch (ex: Exception) {
        // This can occur if we have temporary connectivity glitches to the API server
        if (!watchFailedTimer.isRunning) {
          watchFailedTimer.start()
        }
        val backoffDelay = connectBackoff.nextRetry()
        if (watchFailedTimer.elapsed(TimeUnit.SECONDS) > 60) {
          // k8s uses long polling which can result in temporary connection issues. Only log errors
          // if the problems persist
          log.error(ex) {
            "connectivity lost to k8s for over 60 seconds; waiting ${backoffDelay.toMillis()}ms " +
              "before retrying"
          }
          watchFailedTimer.reset()
          watchFailedTimer.start()
        }
        Thread.sleep(backoffDelay.toMillis())
      }
    }
  }

  companion object {
    private val log = getLogger<KubernetesClusterWatcher>()
    private val podType: java.lang.reflect.Type =
      object : TypeToken<Watch.Response<V1Pod>>() {}.type

    const val CHANGE_TYPE_MODIFIED = "MODIFIED"
    const val CHANGE_TYPE_DELETED = "DELETED"
    const val CHANGE_TYPE_ADDED = "ADDED"
  }
}

private val V1Pod.asClusterMember get() = Cluster.Member(metadata!!.name!!, status!!.podIP ?: "")

private val V1Pod.isReady: Boolean
  get() {
    if (status!!.containerStatuses == null) return false
    if (status!!.containerStatuses!!.any { !it.ready }) return false
    return status!!.podIP?.isNotEmpty() ?: false
  }

private val Watch.Response<V1Pod>.pod: V1Pod get() = `object`

internal fun Watch.Response<V1Pod>.applyTo(cluster: DefaultCluster) {
  val memberSet = setOf(pod.asClusterMember)
  when (type) {
    CHANGE_TYPE_ADDED, CHANGE_TYPE_MODIFIED ->
      if (pod.isReady) cluster.clusterChanged(membersBecomingReady = memberSet)
      else cluster.clusterChanged(membersBecomingNotReady = memberSet)
    CHANGE_TYPE_DELETED -> cluster.clusterChanged(membersBecomingNotReady = memberSet)
  }
}
