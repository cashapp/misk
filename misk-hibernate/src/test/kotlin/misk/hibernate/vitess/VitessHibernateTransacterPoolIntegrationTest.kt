package misk.hibernate.vitess

import jakarta.inject.Inject
import misk.hibernate.CharacterQuery
import misk.hibernate.DbActor
import misk.hibernate.DbCharacter
import misk.hibernate.DbMovie
import misk.hibernate.MovieQuery
import misk.hibernate.Movies
import misk.hibernate.MoviesReader
import misk.hibernate.MoviesTestModule
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.allowTableScan
import misk.hibernate.load
import misk.hibernate.newQuery
import misk.jdbc.DataSourceType
import misk.jdbc.uniqueString
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.vitess.testing.utilities.DockerVitess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.use

/**
 * Integration test that verifies Vitess reader transacter properly uses the reader connection pool
 * and routes queries to replica tablets.
 * 
 * This test uses a real Vitess instance to verify:
 * 1. Reader transacter can be injected separately from writer transacter
 * 2. Reader transacter uses the reader connection pool (database="@replica")
 * 3. Queries through reader transacter are routed to replica tablets
 * 4. Write operations are not allowed through reader transacter
 */
@MiskTest(startService = true)
class VitessHibernateTransacterPoolIntegrationTest {
  @MiskExternalDependency
  private val dockerVitess = DockerVitess()

  @MiskTestModule
  val module = MoviesTestModule(type = DataSourceType.VITESS_MYSQL, allowScatters = true)

  @Inject @Movies lateinit var writerTransacter: Transacter
  @Inject @MoviesReader lateinit var readerTransacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  @Test
  fun `reader transacter can query data written by writer transacter`() {
    val movieId = writerTransacter.transaction { session ->
      val movie = DbMovie("The Matrix", LocalDate.of(1999, 3, 31))
      session.save(movie)
    }

    val readMovie = readerTransacter.transaction { session ->
      queryFactory.newQuery<MovieQuery>()
        .id(movieId)
        .uniqueResult(session)
    }

    assertThat(readMovie).isNotNull
    assertThat(readMovie!!.name).isEqualTo("The Matrix")
  }

  @Test
  fun `reader transacter prevents write operations`() {
    // Attempting to save through reader transacter should fail
    val exception = assertThrows<IllegalStateException> {
      readerTransacter.transaction { session ->
        val movie = DbMovie("Should Fail", LocalDate.of(2023, 1, 1))
        session.save(movie)
      }
    }
    
    assertThat(exception).hasMessageContaining("Saving isn't permitted in a read only session")
  }

  @Test
  fun `reader transacter can perform complex queries with joins`() {
    writerTransacter.transaction { session ->
      val movieId = session.save(DbMovie("Inception", LocalDate.of(2010, 7, 16)))
      val actorId = session.save(DbActor("Leonardo DiCaprio", LocalDate.of(1974, 11, 11)))
      session.save(DbCharacter("Dom Cobb", session.load(movieId), session.load(actorId)))
      
      val movie2Id = session.save(DbMovie("The Dark Knight", LocalDate.of(2008, 7, 18)))
      val actor2Id = session.save(DbActor("Christian Bale", LocalDate.of(1974, 1, 30)))
      session.save(DbCharacter("Bruce Wayne", session.load(movie2Id), session.load(actor2Id)))
    }

    readerTransacter.transaction { session ->
      val characters = queryFactory.newQuery<CharacterQuery>()
        .allowTableScan()
        .list(session)

      assertThat(characters).hasSize(2)
      assertThat(characters.map { it.name }).containsExactlyInAnyOrder(
        "Dom Cobb", "Bruce Wayne"
      )
      
      // Verify joins work properly - access within transaction to avoid lazy initialization issues
      val character = characters.find { it.name == "Dom Cobb" }
      assertThat(character?.actor?.name).isEqualTo("Leonardo DiCaprio")
      assertThat(character?.movie?.name).isEqualTo("Inception")
    }
  }

  @Test
  fun `reader and writer transacters use different connection pools`() {
    val movieId = writerTransacter.transaction { session ->
      val movie = DbMovie("Interstellar", LocalDate.of(2014, 11, 7))
      session.save(movie)
    }

    val readerResult = readerTransacter.transaction { session ->
      queryFactory.newQuery<MovieQuery>()
        .id(movieId)
        .uniqueResult(session)
    }

    assertThat(readerResult).isNotNull
    assertThat(readerResult!!.name).isEqualTo("Interstellar")
    assertThat(writerTransacter.config().database).isEqualTo("@primary")
    assertThat(readerTransacter.config().database).isEqualTo("@replica")
  }

  @Test
  fun `replicaRead on reader transacter works`() {
    val movieId = writerTransacter.transaction { session ->
      val movie = DbMovie("Tenet", LocalDate.of(2020, 9, 3))
      session.save(movie)
    }

    val replicaReadResult = readerTransacter.replicaRead { session ->
      queryFactory.newQuery<MovieQuery>()
        .id(movieId)
        .uniqueResult(session)
    }

    assertThat(replicaReadResult).isNotNull
    assertThat(replicaReadResult!!.name).isEqualTo("Tenet")

    val target = readerTransacter.replicaRead { session ->
      session.useConnection { c ->
        c.createStatement().use {
          it.executeQuery("SHOW VITESS_TARGET").uniqueString()
        }
      }
    }
    assertThat(target).isEqualTo("@replica")

    val exception = assertThrows<IllegalStateException> {
      readerTransacter.replicaRead { session ->
        val movie = DbMovie("Should Fail", LocalDate.of(2023, 1, 1))
        session.save(movie)
      }
    }

    assertThat(exception).hasMessageContaining("Saving isn't permitted in a read only session")
  }

  @Test
  fun `replicaRead on writer transacter works`() {
    val movieId = writerTransacter.transaction { session ->
      val movie = DbMovie("Tenet", LocalDate.of(2020, 9, 3))
      session.save(movie)
    }

    val replicaReadResult = writerTransacter.replicaRead { session ->
      queryFactory.newQuery<MovieQuery>()
        .id(movieId)
        .uniqueResult(session)
    }

    assertThat(replicaReadResult).isNotNull
    assertThat(replicaReadResult!!.name).isEqualTo("Tenet")

   val target = writerTransacter.replicaRead { session ->
      session.useConnection { c ->
        c.createStatement().use {
          it.executeQuery("SHOW VITESS_TARGET").uniqueString()
        }
      }
    }
    assertThat(target).isEqualTo("@replica")

    val exception = assertThrows<IllegalStateException> {
      writerTransacter.replicaRead { session ->
        val movie = DbMovie("Should Fail", LocalDate.of(2023, 1, 1))
        session.save(movie)
      }
    }

    assertThat(exception).hasMessageContaining("Saving isn't permitted in a read only session")
  }

  @Test
  fun `reader transacter respects read-only mode`() {
    val movieId = writerTransacter.transaction { session ->
      val movie = DbMovie("Original Title", LocalDate.of(2023, 1, 1))
      session.save(movie)
    }

    // Try to modify through reader transacter - changes should not persist
    readerTransacter.transaction { session ->
      val movie = queryFactory.newQuery<MovieQuery>()
        .id(movieId)
        .uniqueResult(session)
      movie!!.name = "Modified Title"
    }

    val verifyMovie = writerTransacter.transaction { session ->
      queryFactory.newQuery<MovieQuery>()
        .id(movieId)
        .uniqueResult(session)
    }
    
    assertThat(verifyMovie!!.name).isEqualTo("Original Title")
  }
}
