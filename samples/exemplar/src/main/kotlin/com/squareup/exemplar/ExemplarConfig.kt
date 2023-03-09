package com.squareup.exemplar

import misk.config.Secret
import misk.metrics.backends.prometheus.PrometheusConfig
import misk.web.WebConfig
import misk.web.metadata.config.RedactInConfigTab
import wisp.config.Config

data class ExemplarConfig(
  val apiKey: Secret<String>,
  val web: WebConfig,
  val prometheus: PrometheusConfig,
  @RedactInConfigTab
  val redacted: String
) : Config
