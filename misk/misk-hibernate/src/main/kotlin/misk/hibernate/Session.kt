package misk.hibernate

import java.sql.Connection
import kotlin.reflect.KClass

interface Session {
  val hibernateSession: org.hibernate.Session
  /**
   * @throws IllegalStateException when save is called on a read only session.
   */
  fun <T : DbEntity<T>> save(entity: T): Id<T>
  fun <T : DbEntity<T>> load(id: Id<T>, type: KClass<T>): T
  fun shards(): Set<Shard>
  fun <T> target(shard: Shard, function: () -> T): T
  fun <T> useConnection(work: (Connection) -> T): T
}

inline fun <reified T : DbEntity<T>> Session.load(id: Id<T>): T = load(id, T::class)

fun checkValidShardIdentifier(identifier: String) {
  check(!identifier.isBlank())
  check(!identifier.contains(' '))
  check(!identifier.contains('/'))
}

fun Session.shards(keyspace: Keyspace) = shards().filter { it.keyspace == keyspace }
