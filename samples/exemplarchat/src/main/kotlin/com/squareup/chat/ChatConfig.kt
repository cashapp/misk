package com.squareup.chat

import misk.config.Config
import misk.eventrouter.KubernetesConfig
import misk.web.WebConfig

data class ChatConfig(
  val web: WebConfig,
  val kubernetes: KubernetesConfig = KubernetesConfig()
) : Config
