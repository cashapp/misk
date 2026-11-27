package misk.redis

import com.google.inject.Guice.createInjector
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.writeText
import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.inject.keyOf
import misk.redis.testing.DockerRedis
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.Jedis
import wisp.deployment.PRODUCTION

class RedisNamedUserAuthTest {
  @Test
  fun `authenticates as a named user from a replication group config`(@TempDir tempDir: Path) {
    val endpoint = DockerRedis.replicationGroupConfig.writer_endpoint
    val port = System.getenv("REDIS_PORT")?.toInt() ?: endpoint.port
    val username = "misk-test-${UUID.randomUUID()}"
    val password = "named-user-test-password"
    val configFile = tempDir.resolve("redis_config.yaml")
    configFile.writeText(
      """
      named:
        writer_endpoint: { hostname: ${endpoint.hostname}, port: $port }
        reader_endpoint: { hostname: ${endpoint.hostname}, port: $port }
        redis_auth_password: $password
        redis_auth_username: $username
      legacy:
        writer_endpoint: { hostname: ${endpoint.hostname}, port: $port }
        reader_endpoint: { hostname: ${endpoint.hostname}, port: $port }
        redis_auth_password: $password
      """
        .trimIndent()
    )
    val configs = MiskConfig.load<RedisConfig>("redis-named-user-test", PRODUCTION, listOf(configFile.toFile()))
    assertThat(configs.getValue("legacy").redis_auth_username).isNull()
    assertThat(RedisReplicationGroupConfig(endpoint, endpoint, password).redis_auth_username).isNull()
    val named = configs.getValue("named").copy(timeout_ms = 1_000)
    assertThat(named.redis_auth_username).isEqualTo(username)

    Jedis(endpoint.hostname, port).use { admin ->
      admin.aclSetUser(username, "on", ">$password", "~$username", "+@connection", "+get", "+set")
      try {
        val injector =
          createInjector(
            DeploymentModule(PRODUCTION),
            MiskTestingServiceModule(),
            RedisModule(named, ConnectionPoolConfig(), useSsl = false),
          )
        val redis = injector.getInstance(keyOf<Redis>())
        try {
          redis[username] = "authenticated".encodeUtf8()
          assertThat(redis[username]?.utf8()).isEqualTo("authenticated")
        } finally {
          redis.close()
        }
      } finally {
        admin.aclDelUser(username)
        admin.del(username)
      }
    }
  }
}
