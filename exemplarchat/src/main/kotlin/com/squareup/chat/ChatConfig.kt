package com.squareup.chat

import misk.config.Config
import misk.web.WebConfig

data class ChatConfig(val web: WebConfig) : Config
