package misk.hibernate.vitess

import jakarta.inject.Inject
import javax.persistence.PersistenceException
import misk.hibernate.Constraint
import misk.hibernate.DbMovie
import misk.hibernate.Id
import misk.hibernate.Movies
import misk.hibernate.MoviesTestModule
import misk.hibernate.Operator
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.allowScatter
import misk.hibernate.newQuery
import misk.jdbc.DataSourceType
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.vitess.testing.utilities.DockerVitess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Demonstrates that an empty `IN` collection on a sharding-key column causes a scatter plan at vtgate. After
 * cashapp/misk#3795 disabled server-side prepared statements for VITESS_MYSQL, Hibernate renders an empty `IN`
 * predicate by dropping it from the SQL entirely — so a query like `WHERE id IN ()` is sent to vtgate as a fully
 * unconstrained `SELECT`, which the planner resolves to `engine.Scatter` because no vindex predicate remains. The
 * `--no-scatter` vtgate flag then rejects the plan with `ScatterQueryException`.
 *
 * Three mitigations are validated:
 * 1. `.allowScatter()` query hint — opts the query into a real scatter at vtgate.
 * 2. Caller-side empty-collection guard — short-circuit before issuing the query. Recommended for any code path where
 *    an `IN` collection on the sharding key can legitimately be empty, since an empty `IN` can never match anything and
 *    the query would always return zero rows.
 * 3. (Future, not tested here) Auto short-circuit inside misk-hibernate's query renderer.
 */
@MiskTest(startService = true)
class EmptyInScatterIntegrationTest {
  @MiskExternalDependency
  private val dockerVitess =
    DockerVitess(containerName = "vitess_query_hints_integ_test_db", enableScatters = false, port = 28003)

  @MiskTestModule val module = MoviesTestModule(type = DataSourceType.VITESS_MYSQL, allowScatters = false)

  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  @Test
  fun `empty IN on the sharding key falls through to a scatter`() {
    // If server-side prepared statements were still enabled for VITESS_MYSQL (pre-#3795), this
    // test would FAIL.
    //
    // The reason is that vtgate's `--no-scatter` flag checks the *opcode the planner assigned*
    // to the route, not the runtime fan-out. There are several opcodes (Equal, EqualUnique, IN,
    // MultiEqual, Scatter, ...); `--no-scatter` only rejects plans whose opcode is `Scatter`.
    //
    // What changes between the two paths is the SQL vtgate receives, which in turn changes
    // which opcode the planner picks:
    //
    //   - Client-side rendering (current default after #3795): Hibernate drops the empty `IN`
    //     predicate from the SQL it sends. vtgate sees `SELECT * FROM movies` — no predicate
    //     referencing a vindex column. The planner has no routing key, so the route is left at
    //     its default opcode of `Scatter`. `--no-scatter` rejects it.
    //
    //   - Server-side prepared statements (pre-#3795): Hibernate keeps the predicate as
    //     `id IN (:ids)`. vtgate sees `id` (a hash-vindex column) referenced and picks the `IN`
    //     opcode. At execute time the `IN` primitive looks up which shards contain values in
    //     the bind list; with an empty list, the answer is "zero shards", and the query returns
    //     an empty result. `--no-scatter` never matches because the opcode is `IN`, not
    //     `Scatter`.
    //
    // So this isn't vtgate recognizing the empty IN as a no-op; it's vtgate planning two
    // entirely different routes because the SQL it receives is different.
    val exception =
      assertThrows<PersistenceException> {
        transacter.transaction { session -> queryFactory.newQuery<MoviesByIdQuery>().idIn(emptyList()).list(session) }
      }

    assertThat(exception.cause).isInstanceOf(ScatterQueryException::class.java)
    assertThat(exception.message)
      .contains("Scatter query detected. Must be opted-in through the `allow scatter` Vitess query hint.")
  }

  @Test
  fun `empty IN on the sharding key succeeds when allowScatter hint is set`() {
    // Passes regardless of `useServerPrepStmts`: either path returns an empty result. With
    // server-side prepares it routes via vindex (no scatter, hint is a no-op); with client-side
    // emulation it executes the actual scatter the hint authorized.
    transacter.transaction { session ->
      val result = queryFactory.newQuery<MoviesByIdQuery>().allowScatter().idIn(emptyList()).list(session)
      assertThat(result).isEmpty()
    }
  }

  @Test
  fun `caller-side empty-collection guard avoids the scatter`() {
    // Recommended pattern when an `IN` collection on the sharding key can be empty: short-circuit
    // before calling the query. Avoids both the no-scatter rejection and the wasted query
    // (an empty `IN` can never match anything).
    //
    // Passes regardless of `useServerPrepStmts`: the query is never issued, so the prepared vs
    // client-side distinction doesn't apply.
    val ids = emptyList<Id<DbMovie>>()

    val result =
      if (ids.isEmpty()) {
        emptyList<DbMovie>()
      } else {
        transacter.transaction { session -> queryFactory.newQuery<MoviesByIdQuery>().idIn(ids).list(session) }
      }

    assertThat(result).isEmpty()
  }
}

private interface MoviesByIdQuery : Query<DbMovie> {
  @Constraint(path = "id", operator = Operator.IN) fun idIn(ids: Collection<Id<DbMovie>>): MoviesByIdQuery
}
