package misk.hibernate

import org.hibernate.TypeMismatchException

/**
 * [InvalidChildEntityLoadException] is raised when there is an attempt to retrieve a child entity
 * using an id. Naturally, loading a child by id without a lookup will result in a scatter query,
 * which we want to avoid. Hibernate will catch these with a `TypeMismatchException` as the entity
 * will expect a gid, but we'll rewrite those as `InvalidChildEntityLoadException` for clarity. The
 * expected way to load child entities is to use `loadByGid()`
 */
class InvalidChildEntityLoadException(e: TypeMismatchException) : Exception(e) {
}
