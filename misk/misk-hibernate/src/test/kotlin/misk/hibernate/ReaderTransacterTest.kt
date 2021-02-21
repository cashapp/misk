package misk.hibernate

import misk.jdbc.DataSourceType
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
class MySQLReaderTransacterTest {
  @MiskTestModule
  val module = MoviesTestModule(DataSourceType.MYSQL)

  @Inject @MoviesReader lateinit var readerTransacter: Transacter
  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  @Test
  fun readerHappyPath() {
    // Insert some movies, characters and actors.
    transacter.allowCowrites().transaction { session ->
      val jp = session.save(DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9)))
      val jg = session.save(DbActor("Jeff Goldblum", LocalDate.of(1952, 10, 22)))
      session.save(DbCharacter("Ian Malcolm", session.load(jp), session.load(jg)))
    }

    // Query that data.
    readerTransacter.transaction { session ->
      val query = queryFactory.newQuery<CharacterQuery>()
        .allowTableScan()
        .name("Ian Malcolm")
      val ianMalcolm = query.uniqueResult(session)!!
      assertThat(ianMalcolm.actor?.name).isEqualTo("Jeff Goldblum")
      assertThat(ianMalcolm.movie.name).isEqualTo("Jurassic Park")

      // Shard targeting works.
      val shard = ianMalcolm.rootId.shard(session)
      session.target(shard) {
        assertThat(query.uniqueResult(session)!!.actor?.name).isEqualTo("Jeff Goldblum")
      }
    }
  }

  @Test
  fun readerTransacterWontSave() {
    assertFailsWith<IllegalStateException> {
      readerTransacter.transaction { session ->
        session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
      }
    }
    transacter.transaction { session ->
      assertThat(
        queryFactory.newQuery<MovieQuery>().allowFullScatter().allowTableScan()
          .list(session)
      ).isEmpty()
    }
  }

  @Test
  fun readTransacterWontUpdate() {
    val id: Id<DbMovie> = transacter.transaction { session ->
      session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
    }

    readerTransacter.transaction { session ->
      val movie: DbMovie? = queryFactory.newQuery<MovieQuery>().id(id).uniqueResult(session)
      movie!!.name = "Not Star Wars"
    }

    transacter.transaction { session ->
      val movie: DbMovie? = queryFactory.newQuery<MovieQuery>().id(id).uniqueResult(session)
      assertThat(movie!!.name).isEqualTo("Star Wars")
    }
  }

  @Test
  fun readTransacterWontDelete() {
    val id: Id<DbMovie> = transacter.transaction { session ->
      session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
    }

    assertFailsWith<IllegalStateException> {
      readerTransacter.transaction { session ->
        val movie: DbMovie? = queryFactory.newQuery<MovieQuery>().id(id).uniqueResult(session)
        session.delete(movie!!)
      }
    }

    transacter.transaction { session ->
      val movie: DbMovie? = queryFactory.newQuery<MovieQuery>().id(id).uniqueResult(session)
      assertThat(movie).isNotNull
    }
  }
}
