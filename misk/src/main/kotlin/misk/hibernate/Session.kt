package misk.hibernate

import org.hibernate.query.Query
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery

interface Session {
  fun <T : DbEntity<T>> save(entity: T)
  fun newCriteriaBuilder(): CriteriaBuilder
  fun <T : DbEntity<T>> query(criteria: CriteriaQuery<T>): Query<T>
}
