package misk.hibernate

import kotlin.reflect.KClass

inline fun <reified T> dumpColumn(
  session: Session,
  entityClass: KClass<*>,
  columnName: String
): List<T> {
  val criteriaBuilder = session.hibernateSession.criteriaBuilder
  val criteria = criteriaBuilder.createQuery(T::class.java)
  val notificationRoot = criteria.from(entityClass.java)
  criteria.select(notificationRoot.get(columnName))
  return session.hibernateSession
      .createQuery(criteria)
      .resultList
}