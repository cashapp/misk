package misk.hibernate.vitess

import jakarta.inject.Inject
import javax.persistence.PersistenceException
import misk.hibernate.MovieQuery
import misk.hibernate.Movies
import misk.hibernate.MoviesTestModule
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.allowScatter
import misk.hibernate.newQuery
import misk.jdbc.DataSourceType
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.vitess.VitessQueryHints
import misk.vitess.testing.utilities.DockerVitess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Test suite that verifies that Vitess query hints work as intended in a Vitess environment. */
@MiskTest(startService = true)
class VitessQueryHintsIntegrationTest {
  @MiskExternalDependency
  private val dockerVitess =
    DockerVitess(containerName = "vitess_query_hints_integ_test_db", enableScatters = false, port = 28003)

  @MiskTestModule val module = MoviesTestModule(type = DataSourceType.VITESS_MYSQL, allowScatters = false)

  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  @Test
  fun `scatter query will fail by default but succeed with hint`() {
    val exception =
      assertThrows<PersistenceException> {
        transacter.transaction({ session -> queryFactory.newQuery<MovieQuery>().list(session) })
      }

    assertThat(exception.cause).isInstanceOf(ScatterQueryException::class.java)
    assertThat(exception.message)
      .contains("Scatter query detected. Must be opted-in through the `allow scatter` Vitess query hint.")

    // Now use the `allow scatter` hint, which should not throw an exception.
    transacter.transaction({ session -> queryFactory.newQuery<MovieQuery>().allowScatter().list(session) })
  }

  @Test
  fun `multiple hints with query timeout`() {
    val exception =
      assertThrows<PersistenceException> {
        transacter.transaction({ session ->
          session.hibernateSession
            .createNativeQuery("SELECT SLEEP(2)")
            .addQueryHint(VitessQueryHints.allowScatter())
            .addQueryHint(VitessQueryHints.queryTimeoutMs(1000))
            .list()
        })
      }

    assertThat(exception.message).contains("Query execution was interrupted")
  }
}
