package misk.hibernate

import misk.hibernate.TransacterTest.ActorAndReleaseDate
import misk.hibernate.TransacterTest.CharacterQuery
import misk.hibernate.TransacterTest.MovieNameAndReleaseDate
import java.time.LocalDate
import javax.inject.Singleton
import javax.persistence.criteria.CompoundSelection
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.Path
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root
import kotlin.reflect.KClass

// TODO(jwilson): build this with metaprogramming (reflection/codegen) instead of code.
class ManualCharacterQuery : CharacterQuery {
  private val constraints = mutableListOf<ConstraintSpec>()

  private fun addConstraint(path: List<String>, predicate: PredicateSpec): CharacterQuery {
    constraints.add(ConstraintSpec(path, predicate))
    return this
  }

  override fun name(name: String) = addConstraint(
      path = listOf("name"),
      predicate = PredicateSpec.Eq(name))

  override fun actorName(name: String) = addConstraint(
      path = listOf("actor", "name"),
      predicate = PredicateSpec.Eq(name))

  override fun movieName(name: String) = addConstraint(
      path = listOf("movie", "name"),
      predicate = PredicateSpec.Eq(name))

  override fun movieReleaseDateBefore(upperBound: LocalDate) = addConstraint(
      path = listOf("movie", "release_date"),
      predicate = PredicateSpec.Lt(upperBound))

  override fun uniqueResult(session: Session): DbCharacter? {
    // TODO(jwilson): max results = 2
    val list = list(session)
    require(list.size <= 1) { "expected at most 1 result but was $list" }
    return list.firstOrNull()
  }

  override fun <P : Projection> uniqueResultAs(session: Session, projection: KClass<P>): P? {
    // TODO(jwilson): max results = 2
    val list = listAs(session, projection)
    require(list.size <= 1) { "expected at most 1 result but was $list" }
    return list.firstOrNull()
  }

  override fun list(session: Session): List<DbCharacter> {
    val criteriaBuilder = session.hibernateSession.criteriaBuilder
    val query = criteriaBuilder.createQuery(DbCharacter::class.java)
    val queryRoot = query.from(DbCharacter::class.java)

    val predicate = buildWherePredicate(queryRoot, criteriaBuilder)
    query.where(predicate)

    val typedQuery = session.hibernateSession.createQuery(query)
    return typedQuery.list()
  }

  private fun buildWherePredicate(root: Root<*>, criteriaBuilder: CriteriaBuilder): Predicate? {
    val predicates = constraints.map {
      val path = root.traverse(it.path)
      it.predicate.toHibernate(criteriaBuilder, path)
    }

    return if (predicates.size == 1) {
      predicates[0]
    } else {
      criteriaBuilder.and(*predicates.toTypedArray())
    }
  }

  override fun <P : Projection> listAs(session: Session, projection: KClass<P>): List<P> {
    val criteriaBuilder = session.hibernateSession.criteriaBuilder
    val query = criteriaBuilder.createQuery(Array<Any>::class.java)
    val root: Root<*> = query.from(DbCharacter::class.java)
    query.select(projectionSelection(projection, criteriaBuilder, root))
    query.where(buildWherePredicate(root, criteriaBuilder))
    val rows = session.hibernateSession.createQuery(query).list()
    return rows.map(projectionTransform(projection))
  }

  private fun <P : Projection> projectionSelection(
    projection: KClass<P>,
    criteriaBuilder: CriteriaBuilder,
    queryRoot: Root<*>
  ): CompoundSelection<Array<Any>>? {
    return when (projection) {
      MovieNameAndReleaseDate::class -> criteriaBuilder.array(
          queryRoot.traverse(listOf("movie", "name")),
          queryRoot.traverse(listOf("movie", "release_date")))

      ActorAndReleaseDate::class -> criteriaBuilder.array(
          queryRoot.traverse(listOf("actor", "name")),
          queryRoot.traverse(listOf("movie", "release_date")))

      else -> throw UnsupportedOperationException()
    }
  }

  private fun <P : Projection> projectionTransform(projection: KClass<P>): (Array<Any>) -> P {
    when (projection) {
      MovieNameAndReleaseDate::class -> {
        @Suppress("UNCHECKED_CAST") // Guarded by the runtime check above.
        return { MovieNameAndReleaseDate(it[0] as String, it[1] as LocalDate?) as P }
      }

      ActorAndReleaseDate::class -> {
        @Suppress("UNCHECKED_CAST") // Guarded by the runtime check above.
        return { ActorAndReleaseDate(it[0] as String, it[1] as LocalDate?) as P }
      }

      else -> throw UnsupportedOperationException()
    }
  }

  @Singleton
  class Factory : Query.Factory {
    override fun <T : Query<*>> newQuery(queryClass: KClass<T>): T {
      require(queryClass == CharacterQuery::class)
      @Suppress("UNCHECKED_CAST") // Guarded by the runtime check above.
      return ManualCharacterQuery() as T
    }
  }

  /** A constraint that isn't attached to a session. */
  data class ConstraintSpec(
    val path: List<String>,
    val predicate: PredicateSpec
  )

  /** A predicate that isn't attached to a session. */
  sealed class PredicateSpec {
    data class Eq<T>(var value: T) : PredicateSpec() {
      override fun toHibernate(
        criteriaBuilder: CriteriaBuilder,
        path: Path<*>
      ) = criteriaBuilder.equal(path, value)
    }

    data class Lt<T : Comparable<T>>(var value: T) : PredicateSpec() {
      @Suppress("UNCHECKED_CAST") // Callers must pass paths whose types match.
      override fun toHibernate(
        criteriaBuilder: CriteriaBuilder,
        path: Path<*>
      ) = criteriaBuilder.lessThan(path as Path<T>, value)
    }

    abstract fun toHibernate(criteriaBuilder: CriteriaBuilder, path: Path<*>): Predicate
  }
}

fun Path<*>.traverse(chain: List<String>): Path<*> {
  var result = this
  for (segment in chain) {
    result = result.get<Any>(segment)
  }
  return result
}
