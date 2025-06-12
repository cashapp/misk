package misk.redis.lettuce.cluster.testing

import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.redis.lettuce.RedisService

/**
 * Installs the [RedisClusterReadyService] for testing environments.
 *
 * This module is designed to be used in testing scenarios where Redis clusters need to be
 * verified as operational before tests begin. It is particularly useful for:
 * - Integration tests that depend on Redis clusters
 * - Local development environments using ORC
 * - Any testing scenario where Redis cluster readiness needs to be verified
 *
 * The service will block startup until all Redis clusters are ready and healthy,
 * ensuring that tests don't begin until Redis is fully operational.
 *
 * Usage example:
 * ```kotlin
 * install(RedisClusterReadyServiceModule())
 * ```
 */
class RedisClusterReadyServiceModule : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<RedisClusterReadyService>().enhancedBy<RedisService>())
  }
}