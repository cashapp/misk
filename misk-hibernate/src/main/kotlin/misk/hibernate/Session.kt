package misk.hibernate

import java.sql.Connection
import kotlin.reflect.KClass

interface Session {
  val hibernateSession: org.hibernate.Session
  /**
   * @throws IllegalStateException when save is called on a read only session.
   */
  fun <T : DbEntity<T>> save(entity: T): Id<T>
  fun <T: DbEntity<T>> delete(entity: T)
  fun <T: DbEntity<T>> update(entity: T)
  fun flush()

  fun <T : DbEntity<T>> load(id: Id<T>, type: KClass<T>): T
  fun <R : DbRoot<R>, T : DbSharded<R, T>> loadSharded(gid: Gid<R, T>, type: KClass<T>): T
  fun shards(): Set<Shard>
  fun <T> target(shard: Shard, function: () -> T): T
  fun <T> useConnection(work: (Connection) -> T): T

  /**
   * Registers a hook that fires after the session transaction commits. Post-commit hooks cannot
   * affect the disposition of the transaction; if a post-commit hook fails, the failure
   * will be logged but not propagated to the application, as the transaction will have already
   * committed
   */
  fun onPostCommit(work: () -> Unit)

  /**
   * Registers a hook that fires before the session's transaction commits. Failures in a pre-commit
   * hook will cause the transaction to be rolled back.
   */
  fun onPreCommit(work: () -> Unit)
}

inline fun <reified T : DbEntity<T>> Session.load(id: Id<T>): T = load(id, T::class)
inline fun <R : DbRoot<R>, reified S : DbSharded<R, S>> Session.loadSharded(gid: Gid<R, S>): S = loadSharded(gid, S::class)

fun checkValidShardIdentifier(identifier: String) {
  check(!identifier.isBlank())
  check(!identifier.contains(' '))
  check(!identifier.contains('/'))
}

fun Session.shards(keyspace: Keyspace) = shards().filter { it.keyspace == keyspace }
