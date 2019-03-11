package misk.redis

class RedisConnectionException(cluster: String, cause: Throwable) :
  RuntimeException("Could not connect to redis cluster: $cluster", cause)
