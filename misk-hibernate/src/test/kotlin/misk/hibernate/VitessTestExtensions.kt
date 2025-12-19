package misk.hibernate

import misk.backoff.FlatBackoff
import misk.backoff.RetryConfig
import misk.backoff.retry
import misk.hibernate.annotation.keyspace
import misk.vitess.Shard

object VitessTestExtensions {
  fun <T : DbEntity<T>> Transacter.save(entity: T): Id<T> = transaction { it.save(entity) }

  inline fun <reified T : DbRoot<T>> Id<T>.shard(session: Session): Shard {
    val keyspace = T::class.java.getAnnotation(misk.hibernate.annotation.Keyspace::class.java)
    val shards = session.shards(keyspace.keyspace()).plus(Shard.SINGLE_SHARD)
    return shards.find { it.contains(this.shardKey()) }!!
  }

  fun Transacter.createInSeparateShard(id: Id<DbMovie>, factory: () -> DbMovie): Id<DbMovie> {
    val createdRecordId = createUntil(factory) { session, newId -> newId.shard(session) != id.shard(session) }
    return createdRecordId
  }

  class NotThereYetException : RuntimeException()

  fun Transacter.createInSameShard(id: Id<DbMovie>, factory: () -> DbMovie): Id<DbMovie> {
    val createdRecordId = createUntil(factory) { session, newId -> newId.shard(session) == id.shard(session) }
    return createdRecordId
  }

  inline fun <reified T : DbRoot<T>> Transacter.createUntil(
    crossinline factory: () -> T,
    crossinline condition: (Session, Id<T>) -> Boolean,
  ): Id<T> {
    val retryConfig = RetryConfig.Builder(10, FlatBackoff())
    return retry(retryConfig.build()) {
      transaction { session ->
        val newId = session.save(factory())
        if (!condition(session, newId)) {
          throw NotThereYetException()
        }
        newId
      }
    }
  }
}
