package misk.redis

import misk.inject.KAbstractModule
import redis.clients.jedis.Jedis

class RedisModule(val config: RedisConfig): KAbstractModule() {
  override fun configure() {
    val jedis = Jedis(config.host_name, config.port)
    jedis.auth(config.auth_password)

    val realRedis = RealRedis(jedis)
    bind<Redis>().toInstance(realRedis)
  }
}
