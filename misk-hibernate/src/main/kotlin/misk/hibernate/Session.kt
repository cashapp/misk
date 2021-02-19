package misk.hibernate

import misk.jdbc.Check
import misk.vitess.Keyspace
import misk.vitess.Shard
import java.sql.Connection
import kotlin.reflect.KClass

interface Session {
  val hibernateSession: org.hibernate.Session

  /**
   * @throws IllegalStateException when save is called on a read only session.
   */
  fun <T : DbEntity<T>> save(entity: T): Id<T>

  fun <T : DbEntity<T>> load(id: Id<T>, type: KClass<T>): T
  fun <T : DbEntity<T>> loadOrNull(id: Id<T>, type: KClass<T>): T?
  fun <R : DbRoot<R>, T : DbSharded<R, T>> loadSharded(gid: Gid<R, T>, type: KClass<T>): T
  fun shards(): Set<Shard>
  fun shards(keyspace: Keyspace): Collection<Shard>
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

  /**
   * Registers a hook that fires after a session is closed. This is called regardless if a session
   * was successfully committed or rolled back.
   *
   * A new transaction can be initiated as part of this hook.
   */
  fun onSessionClose(work: () -> Unit)

  /**
   * Disable one or more checks for the duration of the execution of [body]. The passed in checks
   * will entirely replace the other ignored checks at this point, they will not be merged with
   * whatever is there currently.
   *
   * TODO: Deprecate. This can enable checks as a side-effect.
   * Prefer disableChecks() which is cumulative
   */
  fun <T> withoutChecks(vararg checks: Check, body: () -> T): T

  fun <T> disableChecks(checks: Collection<Check>, body: () -> T): T

  /**
   * @throws IllegalStateException when delete is called on a read only session.
   */
  fun <T : DbEntity<T>> delete(entity: T)
}

inline fun <reified T : DbEntity<T>> Session.load(id: Id<T>): T = load(id, T::class)
inline fun <R : DbRoot<R>, reified S : DbSharded<R, S>> Session.loadSharded(gid: Gid<R, S>): S =
  loadSharded(
    gid, S::class
  )

inline fun <reified T : DbEntity<T>> Session.loadOrNull(id: Id<T>): T? = loadOrNull(id, T::class)
