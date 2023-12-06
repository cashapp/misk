package misk.redis.testing

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import com.google.common.base.Stopwatch
import misk.redis.RedisClusterConfig
import misk.redis.RedisClusterReplicationGroupConfig
import misk.redis.RedisNodeConfig
import misk.testing.ExternalDependency
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCluster
import wisp.containers.Composer
import wisp.containers.Container
import wisp.containers.ContainerUtil
import wisp.logging.getLogger
import java.lang.Thread.sleep
import java.time.Duration

/**
 * To use this in tests:
 *
 * 1. Install a `RedisClusterModule` instead of a `FakeRedisModule`.
 *    Make sure to supply the [DockerRedisCluster.config] as the [RedisClusterConfig].
 * 2. Add `@MiskExternalDependency private val dockerRedis: DockerRedisCluster` to your test class.
 */
object DockerRedisCluster : ExternalDependency {
  private val REDIS_CLUSTER_PORTS = listOf(7000, 7001, 7002, 7003, 7004, 7005)
  private const val initialPort = 7000
  private val hostname = if (ContainerUtil.isRunningInDocker) "host.docker.internal" else "localhost"
  private val logger = getLogger<DockerRedisCluster>()
  private const val redisVersion = "7.0.10"

  private val jedis by lazy { JedisCluster(HostAndPort(hostname, initialPort)) }

  private val redisNodeConfig = RedisNodeConfig(hostname, initialPort)
  private val groupConfig = RedisClusterReplicationGroupConfig(
    configuration_endpoint = redisNodeConfig,
    redis_auth_password = "",
    timeout_ms = 1_000,
  )
  val config = RedisClusterConfig(mapOf("test-group" to groupConfig))

  private const val containerName = "misk-redis-cluster-testing"
  private val composer = Composer(
    containerName,
    Container {
      val envVars = listOf("IP=0.0.0.0", "INITIAL_PORT=$initialPort", "MASTERS=3", "SLAVES_PER_MASTER=1")
      withImage("grokzen/redis-cluster:$redisVersion")
      withName(containerName)
      withExposedPorts(*REDIS_CLUSTER_PORTS.map { ExposedPort.tcp(it) }.toTypedArray())
      withHostConfig(
        HostConfig().withPortBindings(Ports().apply {
          REDIS_CLUSTER_PORTS.forEach { port ->
            bind(ExposedPort.tcp(port), Ports.Binding.bindPort(port))
          }
        })
      )
      withEnv(envVars)
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
        //We invoke redis commands to verify connection
        //even if jedis.ping returns a response, it's possible cluster is not assembled yet
        val clusterInfo = jedis.clusterNodes.values.first().resource.use {connection ->
          Jedis(connection).use {
            it.clusterInfo()
          }
        }
        check(clusterInfo.contains("cluster_state:ok"))
        jedis.mget("{key}1", "{key}2")
        logger.info { "Sent GET command, got a reply" }
        break
      } catch (e: Exception) {
        sleep(200)
      }
      if (stopwatch.elapsed() > Duration.ofSeconds(60)) {
        error("Could not get a reply from Redis within 30 seconds. Is the Redis Cluster container running?")
      }
    }
    logger.info { "Redis v$redisVersion is ready!" }
  }

  override fun shutdown() {
    with(jedis) {
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
  DockerRedisCluster.startup()
  // Debug commands here.
  sleep(Duration.ofSeconds(60).toMillis())
  DockerRedisCluster.shutdown()
}

