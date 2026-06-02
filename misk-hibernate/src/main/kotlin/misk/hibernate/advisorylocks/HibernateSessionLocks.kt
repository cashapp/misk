package misk.hibernate.advisorylocks

import misk.hibernate.Id
import org.hibernate.Session

/**
 * Attempts to acquire an advisory lock
 *
 * @param lockKey the string uniquely identifying the lock to acquire
 * @return `true` if we acquired the lock, `false` if the lock was already held by another session
 * @throws IllegalArgumentException if the database instance returns an unexpected result code
 * @throws NotImplementedError if the current dialect is not supported
 */
internal fun Session.tryAcquireLock(lockKey: String): Boolean {
  val dialect = this.sessionFactory.properties["hibernate.dialect"]
  return when (dialect) {
    "org.hibernate.dialect.PostgreSQL95Dialect" -> acquirePostgresLock(lockKey)
    "org.hibernate.dialect.MySQL8Dialect",
    "misk.hibernate.vitess.VitessDialect" -> acquireMySqlLock(lockKey)
    else -> throw NotImplementedError("Unsupported dialect: $dialect")
  }
}

private fun Session.acquireMySqlLock(lockKey: String): Boolean {
  require(lockKey.length <= 64) { "MySQL requires a lock key <= 64 characters, was ${lockKey.length}" }

  val query = this.createNativeQuery("SELECT GET_LOCK(:lockKey, 0)").setParameter("lockKey", lockKey)

  val result = (query.uniqueResult() as Id<*>?)?.id?.toInt() ?: 0

  require(result == 0 || result == 1) { "Unexpected result: $result releasing $lockKey" }

  return result == 1
}

private fun Session.acquirePostgresLock(lockKey: String): Boolean {
  val query = this.createNativeQuery("SELECT pg_try_advisory_lock(hashtext(:lockKey))").setParameter("lockKey", lockKey)

  return (query.uniqueResult() as Boolean)
}

/**
 * Attempts to release an advisory lock
 *
 * @param lockKey the string uniquely identifying the lock to release
 * @throws IllegalStateException if the lock did not exist
 * @throws IllegalArgumentException if the lock could not be released because it was already held by another session
 */
internal fun Session.tryReleaseLock(lockKey: String) {
  val dialect = this.sessionFactory.properties["hibernate.dialect"]
  return when (dialect) {
    "org.hibernate.dialect.PostgreSQL95Dialect" -> releasePostgresLock(lockKey)
    "org.hibernate.dialect.MySQL8Dialect",
    "misk.hibernate.vitess.VitessDialect" -> releaseMySqlLock(lockKey)
    else -> throw NotImplementedError("Unsupported dialect: $dialect")
  }
}

private fun Session.releaseMySqlLock(lockKey: String) {
  val query = this.createNativeQuery("SELECT RELEASE_LOCK(:lockKey)").setParameter("lockKey", lockKey)

  val result =
    (query.uniqueResult() as Id<*>?)?.id?.toInt()
      ?: throw IllegalStateException("Attempting to release lock $lockKey that doesn't exist")

  when (result) {
    0 -> throw IllegalStateException("Attempting to release lock $lockKey owned by another session")
    1 -> {}
    else -> throw IllegalStateException("Unexpected result: $result releasing $lockKey")
  }
}

private fun Session.releasePostgresLock(lockKey: String) {
  val query = this.createNativeQuery("SELECT pg_advisory_unlock(hashtext(:lockKey))").setParameter("lockKey", lockKey)
  val result = (query.uniqueResult() as Boolean)
  check(result) { "Attempting to release lock $lockKey that doesn't exist" }
}
