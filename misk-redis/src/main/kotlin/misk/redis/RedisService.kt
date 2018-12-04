package misk.redis

import com.google.common.util.concurrent.AbstractIdleService
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Controls the connection lifecycle for Redis.
 */
@Singleton
internal class RedisService @Inject internal constructor(
  private val redisProvider: Provider<Redis>
) : AbstractIdleService() {
  private lateinit var redis: Redis

  override fun startUp() {
    redis = redisProvider.get()
  }

  override fun shutDown() {
    if (::redis.isInitialized) redis.close()
  }
}
