package misk.redis.lettuce

import io.lettuce.core.support.BoundedPoolConfig

/**
 * Converts Misk Redis connection pool configuration to Lettuce pool configuration.
 *
 * This extension function maps Misk's Redis connection pool settings to Lettuce's [BoundedPoolConfig], enabling
 * integration between Misk's configuration system and Lettuce's connection pooling implementation.
 */
internal fun RedisConnectionPoolConfig.toBoundedPoolConfig() =
  BoundedPoolConfig.builder()
    .maxTotal(max_total)
    .maxIdle(max_idle)
    .minIdle(min_idle)
    .testOnCreate(test_on_create)
    .testOnAcquire(test_on_acquire)
    .testOnRelease(test_on_release)
    .build()
