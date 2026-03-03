package misk.redis

import misk.config.Config

class RedisConfig : java.util.LinkedHashMap<String, RedisReplicationGroupConfig>, Config {
  constructor() : super()
  constructor(m: Map<String, RedisReplicationGroupConfig>) : super(m)
}

data class RedisReplicationGroupConfig(
  val writer_endpoint: RedisNodeConfig,
  val reader_endpoint: RedisNodeConfig,
  val redis_auth_password: String
)

data class RedisNodeConfig(
  val hostname: String,
  val port: Int
)
