package misk.hibernate

import org.hibernate.SessionFactory
import kotlin.reflect.KClass

internal class RealTransacter(
  private val sessionFactory: SessionFactory
) : Transacter {
  override fun <T> transaction(lambda: (session: Session) -> T): T {
    sessionFactory.openSession().use { session ->
      val transaction = session.beginTransaction()!!
      val realSession = RealSession(session)
      val result: T
      try {
        result = lambda(realSession)
        transaction.commit()
        return result
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