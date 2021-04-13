package com.squareup.chat

import misk.clustering.kubernetes.KubernetesConfig
import misk.metrics.backends.prometheus.PrometheusConfig
import misk.web.WebConfig
import wisp.config.Config

data class ChatConfig(
  val web: WebConfig,
  val prometheus: PrometheusConfig,
  val kubernetes: KubernetesConfig = KubernetesConfig()
) : Config
