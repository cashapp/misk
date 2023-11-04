package com.squareup.exemplar

import com.google.inject.Module
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.inject.Inject
import misk.inject.KAbstractModule
import misk.ratelimiting.bucket4j.redis.RedisBucket4jRateLimiterModule
import misk.redis.testing.DockerRedis
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

@MiskTest(startService = true)
class RedisRateLimitedActionTests : AbstractRateLimitedActionTests() {
  @Suppress("unused")
  @MiskTestModule val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(ExemplarTestModule())
      install(RedisBucket4jRateLimiterModule(DockerRedis.config, JedisPoolConfig(), useSsl = false))
      bind<MeterRegistry>().toInstance(SimpleMeterRegistry())
    }
  }

  @Inject private lateinit var jedisPool: JedisPool

  override fun setException() {
    jedisPool.close()
  }
}
