package misk.ratelimiting.bucket4j.redis

import io.github.bucket4j.distributed.serialization.Mapper
import misk.testing.updateForParallelTests

/**
 * A [KeyMapper] that appends a unique identifier for each test process ID to the Redis key. This is used to
 * ensure that multiple tests can run in parallel without clobbering each other's keys.
 */
internal object ParallelTestsKeyMapper : Mapper<String> {
  private val delegate = Mapper.STRING

  override fun toBytes(value: String): ByteArray = delegate.toBytes(mapValue(value))

  override fun toString(value: String): String = delegate.toString(mapValue(value))

  private fun mapValue(value: String): String =
    value.updateForParallelTests { v, index -> v + "_$index" }
}
