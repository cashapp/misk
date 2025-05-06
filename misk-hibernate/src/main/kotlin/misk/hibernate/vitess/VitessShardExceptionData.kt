package misk.hibernate.vitess

import misk.vitess.Shard

class VitessShardExceptionData(
  val shard: Shard,
  val exceptionMessage: String,
  val isShardHealthError: Boolean,
  val isPrimary: Boolean,
  val causeException: Throwable
) {
  override fun toString(): String {
    return String.format(
      "VitessShardExceptionData{shard=%s, isShardHealthError=%s, isPrimary=%s}",
      shard.toString(), isShardHealthError, isPrimary
    )
  }
}
