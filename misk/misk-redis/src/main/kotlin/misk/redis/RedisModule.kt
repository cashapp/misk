package misk.redis

import com.google.common.util.concurrent.Service
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.inject.KAbstractModule
import redis.clients.jedis.Jedis

class RedisModule : KAbstractModule() {
  override fun configure() {
    multibind<Service>().to<RedisService>()
  }

  @Provides @Singleton
  internal fun provideRedisClient(config: RedisConfig): Redis {
    // Connect to the redis instance
    val jedis = Jedis(config.host_name, config.port, true)
    jedis.auth(config.auth_password)
    return RealRedis(jedis)
  }
}
