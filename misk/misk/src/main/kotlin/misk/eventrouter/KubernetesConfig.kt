package misk.eventrouter

import misk.config.Config

data class KubernetesConfig(
  val my_pod_namespace: String = System.getenv("MY_POD_NAMESPACE"),
  val my_pod_name: String = System.getenv("MY_POD_NAME")
) : Config
