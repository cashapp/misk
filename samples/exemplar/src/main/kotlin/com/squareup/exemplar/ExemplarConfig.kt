package com.squareup.exemplar

import misk.metrics.backends.prometheus.PrometheusConfig
import misk.web.WebConfig
import wisp.config.Config

data class ExemplarConfig(val web: WebConfig, val prometheus: PrometheusConfig) : Config
