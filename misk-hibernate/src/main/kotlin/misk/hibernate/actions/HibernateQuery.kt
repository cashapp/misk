package misk.hibernate.actions

import kotlin.reflect.KClass
import misk.hibernate.DbEntity
import misk.hibernate.Query

/**
 * [HibernateQuery] is a wrapper class that allows for unqualified binding of Misk.Hibernate.Query classes without
 * collision.
 */
internal data class HibernateQuery(val query: KClass<out Query<DbEntity<*>>>)
