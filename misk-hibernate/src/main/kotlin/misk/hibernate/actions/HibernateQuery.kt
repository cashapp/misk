package misk.hibernate.actions

import misk.hibernate.DbEntity
import misk.hibernate.Query
import kotlin.reflect.KClass

/**
 * [HibernateQuery] is a wrapper class that allows for unqualified binding of Misk.Hibernate.Query
 * classes without collision.
 */
data class HibernateQuery(val query: KClass<out Query<DbEntity<*>>>)