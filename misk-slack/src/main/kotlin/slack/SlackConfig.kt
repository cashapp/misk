@file:Suppress("PropertyName") // We use snake_case to match Misk YAML.

package slack

import misk.config.Secret

data class SlackConfig(
  val bearer_token: Secret<String>,
  val signing_secret: Secret<String>
)
