package misk.hibernate.actions

import misk.exceptions.UnauthorizedException
import misk.hibernate.DbActor
import misk.hibernate.DbCharacter
import misk.hibernate.DbMovie
import misk.hibernate.Movies
import misk.hibernate.OperatorsMovieQuery
import misk.hibernate.Transacter
import misk.hibernate.actions.HibernateDatabaseQueryTestingModule.Companion.OPERATORS_MOVIE_QUERY_ACCESS_ENTRY
import misk.hibernate.load
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDate
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
class HibernateDatabaseQueryStaticActionTest {
  @MiskTestModule
  val module = HibernateDatabaseQueryTestingModule()

  @Inject
  private lateinit var realActionRequestExecuter: RealActionRequestExecuter<HibernateDatabaseQueryStaticAction.Request, HibernateDatabaseQueryStaticAction.Response>
  @Inject @Movies lateinit var transacter: Transacter

  @BeforeEach
  fun before() {
    realActionRequestExecuter.requestPath(HibernateDatabaseQueryStaticAction.HIBERNATE_QUERY_STATIC_WEBACTION_PATH)

    // Insert some movies, characters and actors.
    transacter.allowCowrites().transaction { session ->
      val jp = session.save(DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9)))
      val pf = session.save(DbMovie("Pulp Fiction", LocalDate.of(1994, 5, 21)))
      val dh = session.save(DbMovie("Die Hard", LocalDate.of(1988, 7, 15)))
      val jg = session.save(DbActor("Jeff Goldblum", LocalDate.of(1952, 10, 22)))
      session.save(DbCharacter("Ian Malcolm", session.load(jp), session.load(jg)))
    }
  }

  private val AUTHORIZED_CAPABILITIES = OPERATORS_MOVIE_QUERY_ACCESS_ENTRY.capabilities.joinToString() + ",admin_console"

  @Test
  fun `unauthorized request`() {
    assertFailsWith<UnauthorizedException> {
      realActionRequestExecuter.executeRequest(
        HibernateDatabaseQueryStaticAction.Request(
          entityClass = DbMovie::class.simpleName!!,
          queryClass = OperatorsMovieQuery::class.simpleName!!,
          query = mapOf("Select/OperatorsMovieQuery/uniqueName" to true)
        ),
        user = "joey",
        // Lacks OPERATORS_MOVIE_QUERY_ACCESS_ENTRY
        capabilities = "admin_console"
      )
    }
  }

  // TODO (adrw) re-enable once LocalDate Moshi adapter is written/bound to support DbMovie.release_date
  @Disabled
  @Test
  fun `default request`() {
    val results = realActionRequestExecuter.executeRequest(
      HibernateDatabaseQueryStaticAction.Request(
        entityClass = DbMovie::class.simpleName!!,
        queryClass = OperatorsMovieQuery::class.simpleName!!,
        query = mapOf()
      )
    )
    assertThat(results.results).containsAll(
      listOf(
        mapOf("name" to "Jurassic Park", "created_at" to "2018-01-01T00:00:00.000Z"),
        mapOf("name" to "Pulp Fiction", "created_at" to "2018-01-01T00:00:00.000Z"),
        mapOf("name" to "Die Hard", "created_at" to "2018-01-01T00:00:00.000Z"),
      )
    )
  }

  @Test
  fun `static select`() {
    val results = realActionRequestExecuter.executeRequest(
      HibernateDatabaseQueryStaticAction.Request(
        entityClass = DbMovie::class.simpleName!!,
        queryClass = OperatorsMovieQuery::class.simpleName!!,
        query = mapOf("Select/OperatorsMovieQuery/uniqueName" to true)
      ),
      user = "joey",
      capabilities = AUTHORIZED_CAPABILITIES
    )
    assertThat(results.results).containsAll(
      listOf(
        mapOf("name" to "Jurassic Park"),
        mapOf("name" to "Pulp Fiction"),
        mapOf("name" to "Die Hard"),
      )
    )
  }

  @Test
  fun `static constraints`() {
    val results = realActionRequestExecuter.executeRequest(
      HibernateDatabaseQueryStaticAction.Request(
        entityClass = DbMovie::class.simpleName!!,
        queryClass = OperatorsMovieQuery::class.simpleName!!,
        query = mapOf(
          // TODO(adrw) remove limit select paths once it can handle LocalDate
          "Select/OperatorsMovieQuery/uniqueName" to true,
          "Constraint/OperatorsMovieQuery/name" to mapOf(
            "name" to "Die Hard"
          )
        ),
      ),
      user = "joey",
      capabilities = AUTHORIZED_CAPABILITIES
    )
    assertEquals(
      listOf(
        mapOf("name" to "Die Hard"),
      ), results.results
    )
  }

  @Test
  fun `static orders`() {
    val results = realActionRequestExecuter.executeRequest(
      HibernateDatabaseQueryStaticAction.Request(
        entityClass = DbMovie::class.simpleName!!,
        queryClass = OperatorsMovieQuery::class.simpleName!!,
        query = mapOf(
          // TODO(adrw) remove limit select paths once it can handle LocalDate
          "Select/OperatorsMovieQuery/uniqueName" to true,
          "Order/OperatorsMovieQuery/releaseDateAsc" to true
        )
      ),
      user = "joey",
      capabilities = AUTHORIZED_CAPABILITIES
    )
    assertEquals(
      listOf(
        mapOf("name" to "Die Hard"),
        mapOf("name" to "Jurassic Park"),
        mapOf("name" to "Pulp Fiction"),
      ), results.results
    )
  }
}
