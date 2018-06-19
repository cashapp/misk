package misk.hibernate

import org.hibernate.SessionFactory
import kotlin.reflect.KClass

internal class RealTransacter(
  private val sessionFactory: SessionFactory
) : Transacter {

  private val TLS = ThreadLocal<Session>()

  override val inTransaction: Boolean
    get() = TLS.get() != null

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
    check(TLS.get() == null) { "Attempted to start a nested session" }

    val realSession = RealSession(sessionFactory.openSession())
    TLS.set(realSession)

    try {
      return lambda(realSession)
    } finally {
      closeSession()
    }
  }

  private fun closeSession() {
    try {
      TLS.get().hibernateSession.close()
    } finally {
      TLS.remove()
    }
  }

  internal class RealSession(
    val session: org.hibernate.Session
  ) : Session {
    override val hibernateSession = session

    override fun <T : DbEntity<T>> save(entity: T): Id<T> {
      @Suppress("UNCHECKED_CAST") // Entities always use Id<T> as their ID type.
      return session.save(entity) as Id<T>
    }

    override fun <T : DbEntity<T>> load(id: Id<T>, type: KClass<T>): T {
      return session.get(type.java, id)
    }
  }
}
