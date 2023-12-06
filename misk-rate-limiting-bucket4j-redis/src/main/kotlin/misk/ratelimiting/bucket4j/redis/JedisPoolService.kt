package misk.ratelimiting.bucket4j.redis

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import redis.clients.jedis.JedisPool

@Singleton
internal class JedisPoolService @Inject constructor(
  private val jedisPool: JedisPool
) : AbstractIdleService() {
  override fun startUp() {
    jedisPool.preparePool()
  }

  override fun shutDown() {
    jedisPool.close()
  }
}
