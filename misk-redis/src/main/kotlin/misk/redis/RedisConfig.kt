package misk.redis

import misk.config.Config

data class RedisConfig(
  val host_name: String,
  val port: Int,
  val auth_password: String
) : Config
