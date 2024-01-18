package misk.redis

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Provider
import jakarta.inject.Inject
import jakarta.inject.Singleton

/** Controls the connection lifecycle for Redis. */
@Singleton
class RedisService @Inject internal constructor(
  private val redisProvider: Provider<Redis>
) : AbstractIdleService() {
  // We initialize the client in startUp because creating the client will connect it to Redis
  private lateinit var redis: Redis

  override fun startUp() {
    // Create the client and connect to the redis instance
    redis = redisProvider.get()
    redis.prepare()
  }

  override fun shutDown() {
    if (::redis.isInitialized) {
      redis.close()
    }
  }
}
