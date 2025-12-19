package misk.slack.webapi

import misk.config.Secret

data class SlackConfig
@JvmOverloads
constructor(
  val url: String = "https://hooks.slack.com/",
  val bearer_token: Secret<String>,
  val signing_secret: Secret<String>,
  val app_token: Secret<String>? = null,
  val bot_token: Secret<String>? = null,
  val socket_mode_enabled: Boolean = false,
)
