package com.squareup.exemplar

import misk.config.RedactInDashboard
import misk.config.Secret
import misk.metrics.backends.prometheus.PrometheusConfig
import misk.web.WebConfig
import wisp.config.Config

data class ExemplarConfig(
  val apiKey: Secret<String>,
  val web: WebConfig,
  val prometheus: PrometheusConfig,
  @RedactInDashboard
  val redacted: String
) : Config
