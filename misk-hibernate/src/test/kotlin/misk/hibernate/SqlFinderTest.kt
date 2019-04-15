package misk.hibernate

import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import javax.inject.Inject

@MiskTest(startService = true)
class SqlFinderTest {
  @MiskTestModule
  val module = MoviesTestModule(disableCrossShardQueryDetector = true)

  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var finderFactory: SqlFinderFactory

  @Test
  fun simpleSqlQuery() {
    val m1 = NameAndReleaseDate("Rocky 1", LocalDate.of(2018, 1, 1))
    val m2 = NameAndReleaseDate("Rocky 2", LocalDate.of(2018, 1, 2))
    val m3 = NameAndReleaseDate("Rocky 3", LocalDate.of(2018, 1, 3))
    val m4 = NameAndReleaseDate("Rocky 4", LocalDate.of(2018, 1, 4))
    val m5 = NameAndReleaseDate("Rocky 5", LocalDate.of(2018, 1, 5))
    val m98 = NameAndReleaseDate("Rocky 98", null)
    val m99 = NameAndReleaseDate("Rocky 99", null)

    transacter.transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))
      session.save(DbMovie(m2.name, m2.releaseDate))
      session.save(DbMovie(m3.name, m3.releaseDate))
      session.save(DbMovie(m4.name, m4.releaseDate))
      session.save(DbMovie(m5.name, m5.releaseDate))
      session.save(DbMovie(m98.name, m98.releaseDate))
      session.save(DbMovie(m99.name, m99.releaseDate))

      assertThat(finderFactory.newFinder(MovieFinder::class)
          .releaseDateIsNot(m3.releaseDate!!)
          .list(session))
          .hasSize(4)
    }
  }

  interface MovieFinder {
    @Sql("select * from movies where release_date != :release_date")
    fun releaseDateIsNot(release_date: LocalDate): QueryPlan<DbMovie>
  }
}
