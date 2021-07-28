package wisp.launchdarkly

import wisp.client.HttpClientSSLConfig
import wisp.config.Config

data class LaunchDarklyConfig(
  val sdk_key: String,
  val base_uri: String,
  val ssl: HttpClientSSLConfig? = null
) : Config
