package misk.clustering.kubernetes

import wisp.config.Config

/**
 * @property clustering_pod_label_selector Optional Kubernetes label selector to filter which pods
 *     in the namespace are considered to be in the same cluster. If omitted, all healthy pods in
 *     the namespace are included in the cluster.
 *     Ex: "app = helloserver".
 */
data class KubernetesConfig(
  val my_pod_namespace: String = System.getenv("MY_POD_NAMESPACE") ?: "<invalid-namespace>",
  val my_pod_name: String = System.getenv("MY_POD_NAME") ?: "<invalid-pod-name>",
  val my_pod_ip: String = System.getenv("MY_POD_IP") ?: "<invalid-pod-ip>",
  val clustering_pod_label_selector: String? = null,
  // NB(mmihic): kubernetes_watch_read_timeout needs to be long to avoid timeouts during watch
  val kubernetes_watch_read_timeout: Long = 60,
  val kubernetes_read_timeout: Long = 15,
  val kubernetes_connect_timeout: Long = 5
) : Config
