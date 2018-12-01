package misk.clustering.kubernetes

import misk.config.Config

data class KubernetesConfig(
  val my_pod_namespace: String = System.getenv("MY_POD_NAMESPACE") ?: "<invalid-namespace>",
  val my_pod_name: String = System.getenv("MY_POD_NAME") ?: "<invalid-pod-name>",
  val my_pod_ip: String = System.getenv("MY_POD_IP") ?: "<invalid-pod-ip>",
  val kubernetes_watch_read_timeout: Long = 60, // NB(mmihic): Needs to be long to avoid timeouts during watch
  val kubernetes_read_timeout: Long = 15,
  val kubernetes_connect_timeout: Long = 5
) : Config
