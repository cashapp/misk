package misk.redis.lettuce.cluster.testing

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import io.lettuce.core.cluster.RedisClusterClient
import misk.backoff.FlatBackoff
import misk.backoff.RetryConfig
import misk.backoff.retry
import misk.redis.lettuce.cluster.redisClusterClient
import misk.redis.lettuce.cluster.withConnectionBlocking
import misk.redis.lettuce.redisUri
import misk.testing.ExternalDependency
import misk.containers.Composer
import misk.containers.Container
import misk.logging.getLogger
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * A test fixture that provides a Redis Cluster running in Docker.
 *
 * This class manages a containerized Redis Cluster for testing, handling lifecycle
 * operations like startup, shutdown, and test cleanup. It uses Docker to run a
 * multi-node Redis Cluster with a predictable configuration.
 *
 * The cluster is configured with:
 * - 3 master nodes
 * - 1 replica per master (3 replicas total)
 * - 6 total nodes (3 masters + 3 replicas)
 * - Ports 7000-7005 for node communication
 *
 * Example usage:
 * ```kotlin
 * @MiskTest
 * class MyRedisClusterTest {
 *   @MiskExternalDependency
 *   private val redis = DockerRedisCluster()
 *
 *   @MiskTestModule
 *   val module = RedisClusterTestModule(
 *     hostname = redis.seedHost,
 *     port = redis.seedPort
 *   )
 *
 *   @Test
 *   fun testClusterOperations() {
 *     // Redis Cluster is running and ready for testing
 *     // Each test gets a clean cluster state
 *   }
 * }
 * ```
 *
 * Features:
 * - Automatic container lifecycle management
 * - Configurable Redis version
 * - Multi-node cluster setup
 * - Automatic port mapping
 * - Cluster health checking
 * - Automatic data cleanup between tests
 *
 * @property version The Redis version to use (defaults to "7.0.10")
 * @property seedHost The hostname for the seed node (always "localhost" for local testing)
 * @property seedPort The port for the seed node, the external docker port could be remapped (defaults to 7000)
 */
class DockerRedisCluster(
  private val version: String = "7.0.10"
) : ExternalDependency {

  val seedHost: String = SEED_HOSTNAME

  val seedPort: Int = SEED_PORT

  private val redisClient: RedisClusterClient by lazy {
    redisClusterClient(
      redisURI = redisUri(seedHost, seedPort) {
        withTimeout(10.seconds.toJavaDuration())
      },
    )
  }

  private val composer = Composer(
    CONTAINER_NAME,
    Container {
      val envVars =
        listOf("IP=0.0.0.0", "INITIAL_PORT=$SEED_PORT", "MASTERS=3", "SLAVES_PER_MASTER=1")
      withImage("$DOCKER_IMAGE:$version")
      withName(CONTAINER_NAME)
      withExposedPorts(*REDIS_CLUSTER_PORTS.map { ExposedPort.tcp(it) }.toTypedArray())
      withHostConfig(
        HostConfig().withPortBindings(
          Ports().apply {
            REDIS_CLUSTER_PORTS.forEach { port ->
              bind(ExposedPort.tcp(port), Ports.Binding.bindPort(port))
            }
          },
        ),
      )
      withEnv(envVars)
    },
  )


  override fun startup() {
    try {
      composer.start()
    } catch (tr: Throwable) {
      throw IllegalStateException("Could not start Docker client. Is Docker running?", tr)
    }

    val connection =
      retry(RetryConfig.Builder(upTo = 100, withBackoff = FlatBackoff(200.milliseconds.toJavaDuration())).build()) {
        redisClient.connect()
      }

    val slots = try {
      connection.waitForRedisClusterReady(
        timeout = 30.seconds,
        pollInterval = 200.milliseconds,
      ).get()
    } catch (e: TimeoutException) {
      throw IllegalStateException(
        "Could not get a reply from Redis within 30 seconds. Is the Redis cluster container running?",
        e,
      )
    }

    logger.info { "Redis Cluster v$version is ready with $slots slots!" }
  }

  override fun shutdown() {
    redisClient.shutdown()
    composer.stop()
  }

  override fun beforeEach() {
    redisClient.withConnectionBlocking {
      sync().flushall()
    }
  }

  override fun afterEach() {
    // No op.
  }


  companion object {
    private const val CONTAINER_NAME = "misk-redis-cluster-testing"
    private const val DOCKER_IMAGE = "grokzen/redis-cluster"
    private const val SEED_HOSTNAME = "localhost"
    private const val SEED_PORT = 7000
    private val REDIS_CLUSTER_PORTS = listOf(7000, 7001, 7002, 7003, 7004, 7005)


    private val logger = getLogger<DockerRedisCluster>()
  }

}
