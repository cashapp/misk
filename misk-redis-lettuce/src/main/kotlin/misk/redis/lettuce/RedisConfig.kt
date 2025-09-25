@file:Suppress("PropertyName")

package misk.redis.lettuce

import misk.config.Redact
import misk.config.Config

/** Default timeout in milliseconds for Redis operations */
const val DEFAULT_TIMEOUT_MS = 10000

/** Default maximum total connections in the pool */
const val DEFAULT_POOL_MAX_TOTAL = 8

/** Default maximum number of idle connections to maintain */
const val DEFAULT_POOL_MAX_IDLE = 8

/** Default minimum number of idle connections to maintain */
const val DEFAULT_POOL_MIN_IDLE = 0

/** Default setting for testing connections on creation */
const val DEFAULT_POOL_TEST_ON_CREATE = false

/** Default setting for testing connections on acquisition */
const val DEFAULT_POOL_TEST_ON_ACQUIRE = false

/** Default setting for testing connections on release */
const val DEFAULT_POOL_TEST_ON_RELEASE = false

/**
 * Base configuration interface for Redis deployments.
 *
 * This interface serves as a common base for both standalone Redis and Redis Cluster
 * configurations, allowing for polymorphic handling of Redis configurations.
 */
sealed interface AbstractRedisConfig : Config

/**
 * Configuration for standalone Redis deployments.
 *
 * This class maps replication group IDs to their configurations, supporting multiple
 * Redis replication groups in a single application. Each replication group can have
 * its own primary and replica nodes, connection settings, and authentication.
 *
 * Example YAML configuration:
 * ```yaml
 * redis:
 *   cash-misk-suspending-exemplar-003:  # Replication group ID
 *     clientName: "exemplar"
 *     writer_endpoint:
 *       hostname: "redis.example.com"
 *       port: 6379
 *     reader_endpoint:
 *       hostname: "redis-replica.example.com"
 *       port: 6379
 *     redis_auth_password: "secret"
 *     use_ssl: true
 *     timeout_ms: 5000
 *     connectionPoolConfig:
 *       maxTotal: 16
 * ```
 *
 * @see [RedisReplicationGroupConfig] for individual group configuration
 */
class RedisConfig @JvmOverloads constructor(
  m: Map<String, RedisReplicationGroupConfig> = emptyMap()
) : java.util.LinkedHashMap<String, RedisReplicationGroupConfig>(m), AbstractRedisConfig

/**
 * Configuration for a Redis replication group in standalone mode.
 *
 * A replication group consists of a primary node and optional replica nodes,
 * along with connection and pool settings. This configuration supports both
 * read-write operations to the primary and read-only operations to replicas.
 *
 * @property client_name Optional client identifier used in Redis monitoring
 * @property writer_endpoint Configuration for the primary Redis node that handles write operations
 * @property reader_endpoint Optional configuration for a read-only replica node for read operations
 * @property redis_auth_password Authentication password for Redis connections
 * @property use_ssl Whether to use SSL/TLS for Redis connections (default: true)
 * @property timeout_ms Connection and operation timeout in milliseconds (default: 10000ms)
 * @property connection_pool Connection pool settings for this group (default: pooling disabled)
 */
data class RedisReplicationGroupConfig @JvmOverloads constructor(
  val client_name: String? = null,
  val writer_endpoint: RedisNodeConfig,
  val reader_endpoint: RedisNodeConfig? = null,
  @Redact val redis_auth_password: String,
  val use_ssl: Boolean = true,
  val timeout_ms: Int = DEFAULT_TIMEOUT_MS,
  val connection_pool: RedisConnectionPoolConfig = RedisConnectionPoolConfig(),
  val function_code_file_path: String? = null,
)

/**
 * Configuration for Redis Cluster deployments.
 *
 * This class maps cluster group IDs to their configurations, supporting multiple
 * Redis clusters in a single application. Each cluster group uses a configuration
 * endpoint to discover the cluster topology.
 *
 * Example YAML configuration:
 * ```yaml
 * redis_cluster:
 *   cash-misk-suspending-exemplar-003:  # Cluster group ID
 *     clientName: "exemplar"
 *     configuration_endpoint:
 *       hostname: "redis-cluster.example.com"
 *       port: 6379
 *     redis_auth_password: "secret"
 *     use_ssl: true
 *     timeout_ms: 5000
 *     connectionPoolConfig:
 *       maxTotal: 16
 * ```
 *
 * @see [RedisClusterGroupConfig] for individual group configuration
 */
class RedisClusterConfig @JvmOverloads constructor(
  m: Map<String, RedisClusterGroupConfig> = emptyMap()
) : java.util.LinkedHashMap<String, RedisClusterGroupConfig>(m), AbstractRedisConfig

/**
 * Configuration for a Redis Cluster group.
 *
 * A cluster group represents a complete Redis Cluster deployment, accessed through
 * a configuration endpoint that provides cluster topology information.
 *
 * @property client_name Optional client identifier used in Redis monitoring (default: null)
 * @property configuration_endpoint Endpoint for cluster topology discovery
 * @property redis_auth_password Authentication password for cluster connections
 * @property use_ssl Whether to use SSL/TLS for Redis connections (default: true)
 * @property timeout_ms Connection and operation timeout in milliseconds (default: 10000ms)
 * @property connection_pool Connection pool settings for this cluster (default: pooling disabled)
 */
data class RedisClusterGroupConfig @JvmOverloads constructor(
  val client_name: String? = null,
  val configuration_endpoint: RedisNodeConfig,
  @Redact val redis_auth_password: String,
  val use_ssl: Boolean = true,
  val timeout_ms: Int = DEFAULT_TIMEOUT_MS,
  val connection_pool: RedisConnectionPoolConfig = RedisConnectionPoolConfig(),
  val function_code_file_path: String? = null,
)

/**
 * Configuration for Redis connection pooling.
 *
 * This class configures connection pool behavior, including pool size limits
 * and connection testing policies. It supports both basic connection management
 * and advanced connection validation.
 *
 * @property max_total Maximum total connections in the pool (default: 8)
 * @property max_idle Maximum number of idle connections to maintain (default: 8)
 * @property min_idle Minimum number of idle connections to maintain (default: 0)
 * @property test_on_create Whether to test connections when created (default: false)
 * @property test_on_acquire Whether to test connections when acquired from pool (default: false)
 * @property test_on_release Whether to test connections when returned to pool (default: false)
 */
data class RedisConnectionPoolConfig @JvmOverloads constructor(
  val max_total: Int = DEFAULT_POOL_MAX_TOTAL,
  val max_idle: Int = DEFAULT_POOL_MAX_IDLE,
  val min_idle: Int = DEFAULT_POOL_MIN_IDLE,
  val test_on_acquire: Boolean = DEFAULT_POOL_TEST_ON_CREATE,
  val test_on_create: Boolean = DEFAULT_POOL_TEST_ON_ACQUIRE,
  val test_on_release: Boolean = DEFAULT_POOL_TEST_ON_RELEASE,
)

/**
 * Configuration for a Redis node endpoint.
 *
 * This class represents the network location of a Redis node, whether it's
 * a standalone server, primary/replica node, or cluster node.
 *
 * @property hostname The hostname or IP address of the Redis node
 * @property port The port number where Redis is listening
 */
data class RedisNodeConfig(
  val hostname: String,
  val port: Int
)
