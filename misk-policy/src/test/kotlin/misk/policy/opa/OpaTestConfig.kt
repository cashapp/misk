package misk.policy.opa

import misk.config.Config

data class OpaTestConfig(
  val baseUrl: String = "http://localhost:8181",
  val unixSocket: String? = ""
) : Config
