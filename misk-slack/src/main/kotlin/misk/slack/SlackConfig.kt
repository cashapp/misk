package misk.slack

import misk.config.Secret

data class SlackConfig(
  val baseUrl: String = "https://hooks.slack.com/",

  /**
   * The full webhook path, i.e. /services/...
   */
  val webhook_path: Secret<String>,

  /**
   * The channel to post to if the caller doesn't specify one.
   * A service that always posts to one channel should specify this, but a service that operates
   * on other services can instead specify a channel for each message.
   */
  val default_channel: String?
)
