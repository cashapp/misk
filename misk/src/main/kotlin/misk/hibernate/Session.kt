package misk.hibernate

import kotlin.reflect.KClass

interface Session {
  val hibernateSession: org.hibernate.Session
  fun <T : DbEntity<T>> save(entity: T): Id<T>
  fun <T : DbEntity<T>> load(id: Id<T>, type: KClass<T>): T
}

inline fun <reified T : DbEntity<T>> Session.load(id: Id<T>): T = load(id, T::class)
