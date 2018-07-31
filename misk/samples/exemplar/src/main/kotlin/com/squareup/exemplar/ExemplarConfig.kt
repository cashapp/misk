package com.squareup.exemplar

import misk.config.Config
import misk.metrics.backends.prometheus.PrometheusConfig
import misk.web.WebConfig

data class ExemplarConfig(val web: WebConfig, val prometheus: PrometheusConfig) : Config
