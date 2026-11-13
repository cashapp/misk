package misk.redis.lettuce

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for Redis configuration classes.
 */
internal class RedisConfigTest {

  @Test
  fun `RedisReplicationGroupConfig with username`() {
    val config = RedisReplicationGroupConfig(
      client_name = "test-client",
      writer_endpoint = RedisNodeConfig(hostname = "localhost", port = 6379),
      redis_auth_password = "secret-password",
      redis_auth_username = "my-username",
      use_ssl = true,
    )

    assertEquals("my-username", config.redis_auth_username)
    assertEquals("secret-password", config.redis_auth_password)
  }

  @Test
  fun `RedisReplicationGroupConfig without username defaults to null`() {
    val config = RedisReplicationGroupConfig(
      writer_endpoint = RedisNodeConfig(hostname = "localhost", port = 6379),
      redis_auth_password = "secret-password",
    )

    assertNull(config.redis_auth_username)
  }

  @Test
  fun `RedisClusterGroupConfig with username`() {
    val config = RedisClusterGroupConfig(
      client_name = "test-cluster",
      configuration_endpoint = RedisNodeConfig(hostname = "cluster.example.com", port = 6379),
      redis_auth_password = "cluster-password",
      redis_auth_username = "cluster-username",
      use_ssl = true,
    )

    assertEquals("cluster-username", config.redis_auth_username)
    assertEquals("cluster-password", config.redis_auth_password)
  }

  @Test
  fun `RedisClusterGroupConfig without username defaults to null`() {
    val config = RedisClusterGroupConfig(
      configuration_endpoint = RedisNodeConfig(hostname = "cluster.example.com", port = 6379),
      redis_auth_password = "cluster-password",
    )

    assertNull(config.redis_auth_username)
  }
}
