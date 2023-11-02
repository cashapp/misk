package misk.redis.testing

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import com.google.common.base.Stopwatch
import misk.redis.RedisConfig
import misk.redis.RedisNodeConfig
import misk.redis.RedisReplicationGroupConfig
import misk.testing.ExternalDependency
import redis.clients.jedis.JedisPooled
import wisp.containers.Composer
import wisp.containers.Container
import wisp.containers.ContainerUtil.isRunningInDocker
import wisp.logging.getLogger
import java.lang.Thread.sleep
import java.time.Duration

/**
 * While [FakeRedis] is nice for running in-memory tests without standing up an actual Redis
 * instance, it can be useful to verify functionality against an actual Redis.
 *
 * To use this in tests:
 *
 * 1. Install a `RedisModule` instead of a `FakeRedisModule`.
 *    Make sure to supply the [DockerRedis.config] as the [RedisConfig].
 * 2. Add `@MiskExternalDependency private val dockerRedis: DockerRedis` to your test class.
 */
object DockerRedis : ExternalDependency {
  private const val port = 6379
  private val hostname = if (isRunningInDocker) "host.docker.internal" else "localhost"
  private val logger = getLogger<DockerRedis>()
  private const val redisVersion = "6.2"

  private val jedis by lazy { JedisPooled(hostname, port) }

  private val redisNodeConfig = RedisNodeConfig(hostname, port)
  private val groupConfig = RedisReplicationGroupConfig(
    writer_endpoint = redisNodeConfig,
    reader_endpoint = redisNodeConfig,
    // NB: Docker redis images won't accept a start-up password via Container->withCmd.
    // The supported mechanism for setting a password is by mounting a custom Redis config,
    // but we aren't in the business of maintaining a redis image just for our tests, and mounting
    // a volume with test-specific configs is more complicated than it's worth.
    // Hence, we supply a blank password, which will only be accepted in Fake environments.
    redis_auth_password = "",
    timeout_ms = 1_000, // 1 second.
  )
  val config = RedisConfig(mapOf("test-group" to groupConfig))

  private val composer = Composer(
    "misk-redis-testing",
    Container {
      val exposedClientPort = ExposedPort.tcp(port)
      withImage("redis:$redisVersion-alpine")
      withName("misk-redis-testing")
      withExposedPorts(exposedClientPort)
      withHostConfig(
        HostConfig().withPortBindings(Ports().apply {
          bind(exposedClientPort, Ports.Binding.bindPort(port))
        })
      )
    }
  )

  override fun startup() {
    try {
      composer.start()
    } catch (tr: Throwable) {
      throw IllegalStateException("Could not start Docker client. Is Docker running?", tr)
    }
    val stopwatch = Stopwatch.createStarted()

    while (true) {
      try {
        val pong = jedis.ping()
        check(pong == "PONG") { "Unexpected reply from Redis. Aborting!" }
        logger.info { "Sent command PING, got reply $pong" }
        break
      } catch (e: Exception) {
        sleep(100)
      }
      if (stopwatch.elapsed() > Duration.ofSeconds(30)) {
        error("Could not get a reply from Redis within 30 seconds. Is the Redis container running?")
      }
    }
    logger.info { "Redis v$redisVersion is ready!" }
  }

  override fun shutdown() {
    with(jedis) {
      pool.clear()
      close()
    }
    composer.stop()
  }

  override fun beforeEach() {
    jedis.flushAll()
  }

  override fun afterEach() {
    // No op.
  }
}

fun main() {
  DockerRedis.startup()
  // Debug commands here.
  sleep(Duration.ofSeconds(60).toMillis())
  DockerRedis.shutdown()
}

