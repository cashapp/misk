package misk.redis.lettuce

val redisPort: Int by lazy {
  checkNotNull(System.getenv("REDIS_PORT")?.toIntOrNull()) { "'REDIS_PORT' is not set in environment" }
}

val redisSeedPort: Int by lazy {
  checkNotNull(System.getenv("REDIS_CLUSTER_SEED_PORT")?.toIntOrNull()) {
    "'REDIS_CLUSTER_SEED_PORT' is not set in environment"
  }
}
