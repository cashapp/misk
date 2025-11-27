package misk.redis

import misk.redis.Redis.ExpirationOption.GT
import misk.redis.Redis.ExpirationOption.LT
import misk.redis.Redis.ExpirationOption.NX
import misk.redis.Redis.ExpirationOption.XX
import mu.KLogger
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCluster
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.UnifiedJedis
import redis.clients.jedis.args.ExpiryOption

internal fun UnifiedJedis.flushAllWithClusterSupport(logger: KLogger) {
  when (this) {
    is JedisPooled -> this.flushAll()
    is JedisCluster -> {
      // Note: flushAll cannot be broadcast to all nodes in a cluster. We need to flush each node individually.
      this.clusterNodes.forEach { (node, pool) ->
        pool.resource.use { conn ->
          try {
            Jedis(conn).use { jedis -> jedis.flushAll() }
          } catch (e : Exception) {
            logger.error(e) { "Error flushing node $node: + ${e.message}" }
          }
        }
      }
    }
    else -> error("flushAll is not supported for UnifiedJedis implementation ${this.javaClass}")
  }
}

internal fun Redis.ExpirationOption.toJedisOption(): ExpiryOption = when (this) {
  NX -> ExpiryOption.NX
  XX -> ExpiryOption.XX
  GT -> ExpiryOption.GT
  LT -> ExpiryOption.LT
}