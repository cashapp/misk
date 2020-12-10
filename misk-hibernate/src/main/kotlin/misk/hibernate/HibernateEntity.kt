package misk.hibernate

import kotlin.reflect.KClass

/**
 * [HibernateEntity] is a wrapper class that allows for unqualified binding of JPA entity classes
 * without collision.
 */
internal data class HibernateEntity(val entity: KClass<out DbEntity<*>>)
