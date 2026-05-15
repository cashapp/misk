package misk.redis.lettuce.testing.standalone

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import io.lettuce.core.RedisClient
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import misk.backoff.FlatBackoff
import misk.backoff.RetryConfig
import misk.backoff.retry
import misk.containers.Composer
import misk.containers.Container
import misk.logging.getLogger
import misk.redis.lettuce.redisUri
import misk.redis.lettuce.standalone.redisClient
import misk.redis.lettuce.standalone.withConnectionBlocking
import misk.testing.ExternalDependency

/**
 * A test fixture that provides a Redis instance running in Docker.
 *
 * This class manages a containerized Redis server for testing, handling lifecycle operations like startup, shutdown,
 * and test cleanup. It uses Docker to run a Redis instance with a predictable configuration.
 *
 * Example usage:
 * ```kotlin
 * @MiskTest
 * class MyRedisTest {
 *   @MiskExternalDependency
 *   private val redis = DockerRedis()
 *
 *   @MiskTestModule
 *   val module = RedisTestModule(
 *     hostname = redis.host,
 *     port = redis.port
 *   )
 *
 *   @Test
 *   fun testRedisOperations() {
 *     // Redis is running and ready for testing
 *     // Each test gets a clean Redis instance
 *   }
 * }
 * ```
 *
 * Features:
 * - Automatic container lifecycle management
 * - Configurable Redis version
 * - Automatic port mapping
 * - Connection health checking
 * - Automatic data cleanup between tests
 *
 * @property version The Redis version to use (defaults to "7.2")
 * @property host The hostname where Redis is accessible (always "localhost" for local testing)
 * @property port The port where Redis is listening,the external docker port could be remapped (defaults to 6379)
 */
class DockerRedis(private val version: String = "7.2") : ExternalDependency {

  val host: String = HOSTNAME

  val port: Int = DEFAULT_REDIS_PORT

  private val redisClient: RedisClient by lazy {
    redisClient(redisURI = redisUri(host, port) { withTimeout(10.seconds.toJavaDuration()) })
  }
  private val composer =
    Composer(
      CONTAINER_NAME,
      Container {
        val exposedClientPort = ExposedPort.tcp(DEFAULT_REDIS_PORT)
        withImage("redis:$version-alpine")
        withName(CONTAINER_NAME)
        withExposedPorts(exposedClientPort)
        withHostConfig(
          HostConfig()
            .withPortBindings(Ports().apply { bind(exposedClientPort, Ports.Binding.bindPort(DEFAULT_REDIS_PORT)) })
        )
      },
    )

  override fun startup() {
    try {
      composer.start()
    } catch (tr: Throwable) {
      throw IllegalStateException("Could not start Docker client. Is Docker running?", tr)
    }

    retry(RetryConfig.Builder(maxRetries = 100, withBackoff = FlatBackoff(duration = 200.milliseconds.toJavaDuration())).build()) {
      redisClient.withConnectionBlocking { check(sync().ping() == "PONG") { "Unexpected reply from Redis. Aborting!" } }
    }

    logger.info { "Redis v$version is ready!" }
  }

  override fun shutdown() {
    redisClient.shutdown()
    composer.stop()
  }

  override fun beforeEach() {
    redisClient.withConnectionBlocking { sync().flushall() }
  }

  override fun afterEach() {
    // No op.
  }

  companion object {
    private const val HOSTNAME: String = "localhost" // For local development
    private const val DEFAULT_REDIS_PORT = 6379
    private const val CONTAINER_NAME = "misk-redis-testing"

    private val logger = getLogger<DockerRedis>()
  }
}
