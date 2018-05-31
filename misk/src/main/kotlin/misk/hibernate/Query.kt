package misk.hibernate

import kotlin.reflect.KClass

/** Base class for SQL queries. */
interface Query<T> {
  fun uniqueResult(session: Session): T?
  fun <P : Projection> uniqueResultAs(session: Session, projection: KClass<P>): P?

  fun list(session: Session): List<T>
  fun <P : Projection> listAs(session: Session, projection: KClass<P>): List<P>

  /** Creates instances of queries. */
  interface Factory {
    fun <T : Query<*>> newQuery(queryClass: KClass<T>): T
  }
}

inline fun <reified P : Projection> Query<*>.listAs(session: Session) = listAs(session, P::class)

inline fun <reified P : Projection> Query<*>.uniqueResultAs(session: Session) = uniqueResultAs(
    session, P::class)

inline fun <reified T : Query<*>> Query.Factory.newQuery(): T = newQuery(T::class)
