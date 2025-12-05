package misk.redis

import misk.config.Redact
import redis.clients.jedis.JedisCluster.DEFAULT_MAX_ATTEMPTS
import redis.clients.jedis.Protocol
import misk.config.Config

/**
 * Top-level configuration element for all redis clusters
 */
class RedisClusterConfig : LinkedHashMap<String, RedisClusterReplicationGroupConfig>, Config {
  constructor() : super()
  constructor(m: Map<String, RedisClusterReplicationGroupConfig>) : super(m)
}

/**
 * Configuration element for a Redis Cluster
 * @property configuration_endpoint The endpoint of a node in the cluster that can be used to
 * discover the rest of the cluster.
 * @property client_name An optional parameter to identify the client application.
 * @property max_attempts The maximum number of attempts in case of failure.
 * @property redis_auth_password The password to use for the connection to the cluster.
 * @property timeout_ms The connection and socket timeout in milliseconds.
 */
data class RedisClusterReplicationGroupConfig @JvmOverloads constructor(
  val configuration_endpoint: RedisNodeConfig,
  val client_name: String? = null,
  val max_attempts: Int = DEFAULT_MAX_ATTEMPTS,
  @Redact
  val redis_auth_password: String,
  val user: String,
  val timeout_ms: Int = Protocol.DEFAULT_TIMEOUT
)

