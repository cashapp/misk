package misk.policy.opa

import misk.config.Config

data class OpaConfig
@JvmOverloads
constructor(
  val baseUrl: String,
  val unixSocket: String?,
  val provenance: Boolean = false,
  val metrics: Boolean = true,
) : Config
