package com.squareup.exemplar

import misk.config.Config
import misk.web.WebConfig

data class ExemplarConfig(val web: WebConfig) : Config
