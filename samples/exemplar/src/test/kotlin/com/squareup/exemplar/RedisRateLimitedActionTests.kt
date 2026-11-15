package com.squareup.exemplar

import com.google.inject.Module
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.inject.Inject
import misk.inject.KAbstractModule
import misk.ratelimiting.bucket4j.redis.RedisBucket4jRateLimiterModule
import misk.redis.Redis
import misk.redis.RedisModule
import misk.redis.testing.DockerRedis
import misk.redis.testing.RedisTestFlushModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import redis.clients.jedis.ConnectionPoolConfig

@MiskTest(startService = true)
class RedisRateLimitedActionTests : AbstractRateLimitedActionTests() {
  @Suppress("unused")
  @MiskTestModule
  val module: Module =
    object : KAbstractModule() {
      override fun configure() {
        install(ExemplarTestModule())
        install(RedisModule(DockerRedis.replicationGroupConfig, ConnectionPoolConfig(), useSsl = false))
        install(RedisBucket4jRateLimiterModule())
        install(RedisTestFlushModule())
        bind<MeterRegistry>().toInstance(SimpleMeterRegistry())
      }
    }

  @Inject private lateinit var redis: Redis

  override fun setException() {
    redis.close()
  }
}
