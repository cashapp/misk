package misk.hibernate

import org.hibernate.SessionFactory
import javax.persistence.criteria.CriteriaQuery

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
    override fun <T : DbEntity<T>> save(entity: T) {
      session.save(entity)
    }

    // TODO(jwilson): replace with a typesafe criteria builder.
    override fun newCriteriaBuilder() = session.entityManagerFactory.criteriaBuilder

    // TODO(jwilson): this should be method on the typesafe criteria builder.
    override fun <T : DbEntity<T>> query(criteria: CriteriaQuery<T>) = session.createQuery(criteria)
  }
}