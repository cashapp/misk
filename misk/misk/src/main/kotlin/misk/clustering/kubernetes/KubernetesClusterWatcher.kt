package misk.clustering.kubernetes

import com.google.common.reflect.TypeToken
import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import io.kubernetes.client.apis.CoreV1Api
import io.kubernetes.client.models.V1Pod
import io.kubernetes.client.util.Config
import io.kubernetes.client.util.Watch
import misk.DependentService
import misk.backoff.ExponentialBackoff
import misk.logging.getLogger
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/**
 * [KubernetesClusterWatcher] watches for changes in the Kubernetes namespace to which this
 * service belongs, and sends watch events to the corresponding [KubernetesClusterService]
 * which determines changes to the cluster and forwards them to the application. We separate
 * cluster watching from managing cluster membership to allow each to be tested in isolation
 */
@Singleton
internal class KubernetesClusterWatcher @Inject internal constructor (
  private val clusterService: KubernetesClusterService,
  private val config: KubernetesConfig
): AbstractIdleService(), DependentService {
  private val running = AtomicBoolean(false)

  override val consumedKeys: Set<Key<*>> = setOf(Key.get(KubernetesClusterService::class.java))
  override val producedKeys: Set<Key<*>> = setOf()

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
    client.httpClient.setReadTimeout(config.kubernetes_read_timeout, TimeUnit.SECONDS)
    client.httpClient.setConnectTimeout(config.kubernetes_connect_timeout, TimeUnit.SECONDS)

    val api = CoreV1Api(client)
    val connectBackoff = ExponentialBackoff(Duration.ofMillis(100), Duration.ofSeconds(5))

    while (running.get()) {
      try {
        log.info { "preparing watch for namespace ${config.my_pod_namespace}" }
        val watch = Watch.createWatch<V1Pod>(
            client,
            api.listNamespacedPodCall(
                config.my_pod_namespace, // namespace
                null, // pretty
                null, // _continue
                null, // fieldSelector
                false, // includeUninitialized
                null, // labelSelector
                null, // limit
                null, // resourceVersion
                null, // timeoutSeconds
                true, // watch
                null, // progressListener
                null  // progressRequestListener
            ),
            podType)

        for (response in watch) {
          log.info { "received watch in namespace ${config.my_pod_namespace}" }
          connectBackoff.reset()
          if (!running.get()) {
            watch.close()
            return
          }

          clusterService.clusterChanged(response)
        }
      } catch (ex: Exception) {
        // This can occur if we have temporary connectivity glitches to the API server
        val backoffDelay = connectBackoff.nextRetry()
        log.error(ex) {
          "connectivity lost to k8s; waiting ${backoffDelay.toMillis()}ms before retrying"
        }

        Thread.sleep(backoffDelay.toMillis())
      }
    }
  }

  private companion object {
    val log = getLogger<KubernetesClusterWatcher>()
    val podType: java.lang.reflect.Type = object : TypeToken<Watch.Response<V1Pod>>() {}.type
  }
}