package misk.hibernate

import misk.backoff.FlatBackoff
import misk.backoff.retry
import misk.hibernate.annotation.keyspace

fun <T : DbEntity<T>> Transacter.save(entity: T): Id<T> = transaction { it.save(entity) }

inline fun <reified T : DbRoot<T>> Transacter.createInSeparateShard(
  id: Id<T>,
  crossinline factory: () -> T
): Id<T> {
  val sw = createUntil(factory) { session, newId ->
    newId.shard(session) != id.shard(session)
  }
  return sw
}

inline fun <reified T : DbRoot<T>> Transacter.createInSameShard(
  id: Id<T>,
  crossinline factory: () -> T
): Id<T> {
  val sw = createUntil(factory) { session, newId ->
    newId.shard(session) == id.shard(session)
  }
  return sw
}

class NotThereYetException : RuntimeException()

inline fun <reified T : DbRoot<T>> Transacter.createUntil(
  crossinline factory: () -> T,
  crossinline condition: (Session, Id<T>) -> Boolean
): Id<T> = retry(10, FlatBackoff()) {
  transaction { session ->
    val newId = session.save(factory())
    if (!condition(session, newId)) {
      throw NotThereYetException()
    }
    newId
  }
}

inline fun <reified T : DbRoot<T>> Id<T>.shard(session: Session): Shard {
  val keyspace = T::class.java.getAnnotation(misk.hibernate.annotation.Keyspace::class.java)
  val shards = session.shards(keyspace.keyspace())
  return shards.find { it.contains(this.shardKey()) }!!
}
