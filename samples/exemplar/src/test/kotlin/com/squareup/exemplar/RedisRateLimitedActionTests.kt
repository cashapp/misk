package com.squareup.exemplar

import com.google.inject.Module
import com.google.inject.Provides
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import jakarta.inject.Singleton
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
    }


    @Provides @Singleton
    // In prod this is provided by Skim
    fun provideMeterRegistry(collectorRegistry: CollectorRegistry): MeterRegistry {
      return PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT, collectorRegistry, Clock.SYSTEM
      )
    }
  }

  @Inject private lateinit var jedisPool: JedisPool

  override fun setException() {
    jedisPool.close()
  }
}
