package misk.redis.lettuce.cluster.testing

import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.redis.lettuce.RedisService

class RedisClusterReadyServiceModule : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<RedisClusterReadyService>().enhancedBy<RedisService>())
  }
}
