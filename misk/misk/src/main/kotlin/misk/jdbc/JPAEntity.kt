package misk.jdbc

/**
 * [JPAEntity] is a wrapper class that allows for unqualified
 * binding of JPA entity classes without collision.
 */
data class JPAEntity(val entity: Class<*>)
