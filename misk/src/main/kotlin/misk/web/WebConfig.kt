package misk.web

import misk.config.Config

data class WebConfig(
    val port: Int,
    val idle_timeout: Long
) : Config
