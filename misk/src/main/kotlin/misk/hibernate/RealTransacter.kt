package misk.hibernate

import org.hibernate.SessionFactory
import kotlin.reflect.KClass

internal class RealTransacter(
  private val sessionFactory: SessionFactory
) : Transacter {
  override fun <T> transaction(lambda: (session: Session) -> T): T {
    // TODO(jwilson): rollback on exception
    sessionFactory.openSession().use { session ->
      val transaction = session.beginTransaction()
      val realSession = RealSession(session)
      val result = lambda(realSession)
      transaction.commit()
      return result
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