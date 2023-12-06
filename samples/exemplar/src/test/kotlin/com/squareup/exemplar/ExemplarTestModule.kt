package com.squareup.exemplar

import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.ratelimiting.bucket4j.redis.RedisBucket4jRateLimiterModule
import misk.redis.testing.DockerRedis
import misk.time.FakeClockModule
import misk.tokens.FakeTokenGeneratorModule
import redis.clients.jedis.JedisPoolConfig
import wisp.deployment.TESTING

class ExemplarTestModule : KAbstractModule() {
  override fun configure() {
    install(DeploymentModule(TESTING))
    install(FakeClockModule())
    install(FakeTokenGeneratorModule())
    install(MiskTestingServiceModule())
  }
}
