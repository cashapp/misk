package misk.eventrouter

import misk.config.Config

data class KubernetesConfig(
  val my_pod_namespace: String = System.getenv("MY_POD_NAMESPACE"),
  val my_pod_name: String = System.getenv("MY_POD_NAME"),
  val kubernetes_read_timeout: Long = 15,
  val kubernetes_connect_timeout: Long = 5
) : Config
