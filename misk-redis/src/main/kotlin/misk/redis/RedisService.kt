package misk.redis

import com.google.common.util.concurrent.AbstractIdleService
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

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
  }

  override fun shutDown() {
    // If the redis client variable was initialized
    if (::redis.isInitialized) {
      // If the redis client was a real client, close the connection
      (redis as? RealRedis)?.close()
    }
  }
}
