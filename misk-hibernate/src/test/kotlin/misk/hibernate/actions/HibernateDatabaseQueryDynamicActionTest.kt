package misk.hibernate.actions

import misk.exceptions.UnauthorizedException
import misk.hibernate.DbActor
import misk.hibernate.DbCharacter
import misk.hibernate.DbMovie
import misk.hibernate.Movies
import misk.hibernate.Operator
import misk.hibernate.Transacter
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
class HibernateDatabaseQueryDynamicActionTest {
  @MiskTestModule
  val module = HibernateDatabaseQueryTestingModule()

  @Inject
  private lateinit var realActionRequestExecuter: RealActionRequestExecuter<HibernateDatabaseQueryDynamicAction.Request, HibernateDatabaseQueryDynamicAction.Response>
  @Inject @Movies lateinit var transacter: Transacter

  @BeforeEach
  fun before() {
    realActionRequestExecuter.requestPath(HibernateDatabaseQueryDynamicAction.HIBERNATE_QUERY_DYNAMIC_WEBACTION_PATH)

    // Insert some movies, characters and actors.
    transacter.allowCowrites().transaction { session ->
      val jp = session.save(DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9)))
      val pf = session.save(DbMovie("Pulp Fiction", LocalDate.of(1994, 5, 21)))
      val dh = session.save(DbMovie("Die Hard", LocalDate.of(1988, 7, 15)))
      val jg = session.save(DbActor("Jeff Goldblum", LocalDate.of(1952, 10, 22)))
      session.save(DbCharacter("Ian Malcolm", session.load(jp), session.load(jg)))
    }
  }

  private val AUTHORIZED_CAPABILITIES =
    HibernateDatabaseQueryTestingModule.DYNAMIC_MOVIE_QUERY_ACCESS_ENTRY.capabilities.joinToString() + ",admin_console"

  @Test
  fun `unauthorized request`() {
    assertFailsWith<UnauthorizedException> {
      realActionRequestExecuter.executeRequest(
        HibernateDatabaseQueryDynamicAction.Request(
          entityClass = DbMovie::class.simpleName!!,
          queryClass = "DbMovieDynamicQuery",
          query = HibernateDatabaseQueryMetadataFactory.Companion.DynamicQuery(
            select = HibernateDatabaseQueryMetadataFactory.Companion.DynamicQuerySelect(
              paths = listOf("name", "created_at")
            )
          )
        ),
        user = "joey",
        // Lacks DYNAMIC_MOVIE_QUERY_ACCESS_ENTRY
        capabilities = "admin_console"
      )
    }
  }

  // TODO (adrw) re-enable once LocalDate Moshi adapter is written/bound to support DbMovie.release_date
  @Disabled
  @Test
  fun `default request`() {
    val results = realActionRequestExecuter.executeRequest(
      HibernateDatabaseQueryDynamicAction.Request(
        entityClass = DbMovie::class.simpleName!!,
        queryClass = "DbMovieDynamicQuery",
        query = HibernateDatabaseQueryMetadataFactory.Companion.DynamicQuery(
        )
      ),
      user = "joey",
      capabilities = AUTHORIZED_CAPABILITIES
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
  fun `dynamic select`() {
    val results = realActionRequestExecuter.executeRequest(
      HibernateDatabaseQueryDynamicAction.Request(
        entityClass = DbMovie::class.simpleName!!,
        queryClass = "DbMovieDynamicQuery",
        query = HibernateDatabaseQueryMetadataFactory.Companion.DynamicQuery(
          select = HibernateDatabaseQueryMetadataFactory.Companion.DynamicQuerySelect(
            paths = listOf("name", "created_at")
          )
        )
      ),
      user = "joey",
      capabilities = AUTHORIZED_CAPABILITIES
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
  fun `dynamic constraints`() {
    val results = realActionRequestExecuter.executeRequest(
      HibernateDatabaseQueryDynamicAction.Request(
        entityClass = DbMovie::class.simpleName!!,
        queryClass = "DbMovieDynamicQuery",
        query = HibernateDatabaseQueryMetadataFactory.Companion.DynamicQuery(
          constraints = listOf(
            HibernateDatabaseQueryMetadataFactory.Companion.DynamicQueryConstraint(
              path = "name",
              operator = Operator.EQ,
              value = "Die Hard"
            )
          ),
          select = HibernateDatabaseQueryMetadataFactory.Companion.DynamicQuerySelect(
            paths = listOf("name", "created_at")
          )
        )
      ),
      user = "joey",
      capabilities = AUTHORIZED_CAPABILITIES
    )
    assertEquals(
      listOf(
        mapOf("name" to "Die Hard", "created_at" to "2018-01-01T00:00:00.000Z"),
      ), results.results
    )
  }

  @Test
  fun `dynamic orders`() {
    val results = realActionRequestExecuter.executeRequest(
      HibernateDatabaseQueryDynamicAction.Request(
        entityClass = DbMovie::class.simpleName!!,
        queryClass = "DbMovieDynamicQuery",
        query = HibernateDatabaseQueryMetadataFactory.Companion.DynamicQuery(
          orders = listOf(
            HibernateDatabaseQueryMetadataFactory.Companion.DynamicQueryOrder(
              path = "id",
              ascending = true
            )
          ),
          select = HibernateDatabaseQueryMetadataFactory.Companion.DynamicQuerySelect(
            paths = listOf("name")
          )
        )
      ),
      user = "joey",
      capabilities = AUTHORIZED_CAPABILITIES
    )
    assertEquals(
      listOf(
        mapOf("name" to "Jurassic Park"),
        mapOf("name" to "Pulp Fiction"),
        mapOf("name" to "Die Hard"),
      ), results.results
    )
  }
}