package misk.redis

import misk.config.Config
import misk.config.Redact
import redis.clients.jedis.Protocol

class RedisConfig : java.util.LinkedHashMap<String, RedisReplicationGroupConfig>, Config {
  constructor() : super()

  constructor(m: Map<String, RedisReplicationGroupConfig>) : super(m)
}

data class RedisReplicationGroupConfig(
  val writer_endpoint: RedisNodeConfig,
  val reader_endpoint: RedisNodeConfig,
  @Redact val redis_auth_password: String,
  val timeout_ms: Int = Protocol.DEFAULT_TIMEOUT,
  val redis_auth_username: String? = null,
) {
  // Retain the JVM constructor and copy signatures used by existing clients.
  @JvmOverloads
  constructor(
    writer_endpoint: RedisNodeConfig,
    reader_endpoint: RedisNodeConfig,
    redis_auth_password: String,
    timeout_ms: Int = Protocol.DEFAULT_TIMEOUT,
  ) : this(writer_endpoint, reader_endpoint, redis_auth_password, timeout_ms, null)

  fun copy(
    writer_endpoint: RedisNodeConfig = this.writer_endpoint,
    reader_endpoint: RedisNodeConfig = this.reader_endpoint,
    redis_auth_password: String = this.redis_auth_password,
    timeout_ms: Int = this.timeout_ms,
  ): RedisReplicationGroupConfig =
    copy(writer_endpoint, reader_endpoint, redis_auth_password, timeout_ms, redis_auth_username)
}

data class RedisNodeConfig(val hostname: String?, val port: Int)
