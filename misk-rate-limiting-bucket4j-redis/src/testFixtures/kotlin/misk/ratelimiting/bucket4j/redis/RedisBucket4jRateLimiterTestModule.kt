package misk.ratelimiting.bucket4j.redis

import misk.inject.KAbstractModule

/**
 * Module for a Redis-backed Bucket4j rate limiter for use in tests, internally using
 * [ParallelTestsKeyMapper] to avoid key collisions when tests are run in parallel.
 */
class RedisBucket4jRateLimiterTestModule(
  private val qualifier: Annotation? = null,
) : KAbstractModule() {
  override fun configure() {
    install(
      RedisBucket4jRateLimiterModule(
        qualifier = qualifier,
        keyMapper = ParallelTestsKeyMapper
      )
    )
  }
}