package wisp.ratelimiting.bucket4j

import io.github.bucket4j.distributed.remote.RemoteBucketState
import io.github.bucket4j.distributed.serialization.DataOutputSerializationAdapter
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import wisp.ratelimiting.RateLimitPruner

abstract class Bucket4jPruner : RateLimitPruner {
  abstract val clockTimeMeter: ClockTimeMeter

  protected fun isBucketStale(state: RemoteBucketState): Boolean {
    val refillTimeNanos = state.calculateFullRefillingTime(clockTimeMeter.currentTimeNanos())
    return refillTimeNanos <= 0L
  }

  protected fun deserializeState(bytes: ByteArray): RemoteBucketState {
    val inputStream = DataInputStream(ByteArrayInputStream(bytes))
    return RemoteBucketState.SERIALIZATION_HANDLE.deserialize(DataOutputSerializationAdapter.INSTANCE, inputStream)
  }
}
