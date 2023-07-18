package misk.slack.webapi

import misk.config.Secret

data class SlackConfig(
  val url: String = "https://hooks.slack.com/",
  val bearer_token: Secret<String>,
  val signing_secret: Secret<String>,
)
