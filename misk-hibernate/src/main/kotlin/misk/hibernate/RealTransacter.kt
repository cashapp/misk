package misk.hibernate

import com.google.common.collect.ImmutableSet
import org.hibernate.SessionFactory
import java.sql.Connection
import java.sql.ResultSet
import kotlin.reflect.KClass

internal class RealTransacter(
  private val sessionFactory: SessionFactory,
  private val config: DataSourceConfig
) : Transacter {

  private val threadLocalSession = ThreadLocal<Session>()

  override val inTransaction: Boolean
    get() = threadLocalSession.get() != null

  override fun <T> transaction(lambda: (session: Session) -> T): T {
    return withSession { session ->
      val transaction = session.hibernateSession.beginTransaction()!!
      val result: T
      try {
        result = lambda(session)
        transaction.commit()
        return@withSession result
      } catch (e: Throwable) {
        if (transaction.isActive) {
          try {
            transaction.rollback()
          } catch (suppressed: Exception) {
            e.addSuppressed(suppressed)
          }
        }
        throw e
      }
    }
  }

  private fun <T> withSession(lambda: (session: Session) -> T): T {
    check(threadLocalSession.get() == null) { "Attempted to start a nested session" }

    val realSession = RealSession(sessionFactory.openSession(), config)
    threadLocalSession.set(realSession)

    try {
      return lambda(realSession)
    } finally {
      closeSession()
    }
  }

  private fun closeSession() {
    try {
      threadLocalSession.get().hibernateSession.close()
    } finally {
      threadLocalSession.remove()
    }
  }

  internal class RealSession(
    val session: org.hibernate.Session,
    val config: DataSourceConfig
  ) : Session {
    override val hibernateSession = session

    override fun <T : DbEntity<T>> save(entity: T): Id<T> {
      @Suppress("UNCHECKED_CAST") // Entities always use Id<T> as their ID type.
      return session.save(entity) as Id<T>
    }

    override fun <T : DbEntity<T>> load(id: Id<T>, type: KClass<T>): T {
      return session.get(type.java, id)
    }

    override fun shards(): Set<Shard> {
      if (config.type == DataSourceType.VITESS) {
        return useConnection { connection ->
          connection.createStatement().use {
            it.executeQuery("SHOW VITESS_SHARDS")
                .map { parseShard(it.getString(1)) }
                .toSet()
          }
        }
      } else {
        return SINGLE_SHARD_SET
      }
    }

    private fun parseShard(string: String): Shard {
      val (keyspace, shard) = string.split('/', limit = 2)
      return Shard(Keyspace(keyspace), shard)
    }

    override fun <T> target(shard: Shard, function: () -> T): T {
      if (config.type == DataSourceType.VITESS) {
        return useConnection { connection ->
          val previousTarget = connection.createStatement().use { statement ->
            val rs = statement.executeQuery("SELECT database()")
            check(rs.next())
            val previousTarget = rs.getString(1)
            statement.execute("USE `$shard`")
            previousTarget
          }
          try {
            function()
          } finally {
            val sql = if (previousTarget.isBlank()) {
              "USE"
            } else {
              "USE `$previousTarget`"
            }
            connection.createStatement().use { it.execute(sql) }
          }
        }
      } else {
        return function();
      }
    }

    override fun <T> useConnection(work: (Connection) -> T): T {
      return session.doReturningWork(work)
    }

    companion object {
      val SINGLE_KEYSPACE = Keyspace("keyspace")
      val SINGLE_SHARD = Shard(SINGLE_KEYSPACE, "0")
      val SINGLE_SHARD_SET = ImmutableSet.of(SINGLE_SHARD)
    }
  }
}

private fun <T> ResultSet.map(function: (ResultSet) -> T): List<T> {
  val result = mutableListOf<T>()
  while (this.next()) {
    result.add(function(this))
  }
  return result
}
