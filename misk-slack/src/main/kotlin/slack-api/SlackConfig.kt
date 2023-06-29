@file:Suppress("PropertyName") // We use snake_case to match Misk YAML.

package `slack-api`

import misk.config.Secret

data class SlackConfig(
  val url: String = "https://hooks.slack.com/",
  val bearer_token: Secret<String>,
  val signing_secret: Secret<String>,
)
