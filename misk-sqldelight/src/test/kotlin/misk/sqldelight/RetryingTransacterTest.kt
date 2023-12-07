package misk.sqldelight

import app.cash.sqldelight.db.OptimisticLockException
import jakarta.inject.Inject
import misk.sqldelight.testing.Movies
import misk.sqldelight.testing.MoviesDatabase
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@MiskTest(startService = true)
class RetryingTransacterTest {
  @MiskTestModule
  val module = SqlDelightTestModule()

  class NonRetriableException() : RuntimeException()

  @Inject lateinit var moviesDatabase: MoviesDatabase

  private val id = 4L
  private val title = "Fellowship of the Ring"
  private val newTitle = "The Two Towers"

  @Test fun transactionRetriesAndSucceeds() {
    moviesDatabase.transaction {
      moviesDatabase.moviesQueries.createMovie(id, title)
    }

    var version = -2L

    moviesDatabase.transaction {
      version += 1
      moviesDatabase.moviesQueries.updateMovie(newTitle, Movies.Version(version), id)
    }

    val retrievedTitle = moviesDatabase.transactionWithResult {
      moviesDatabase.moviesQueries.getMovie(id).executeAsOne().title
    }

    assertThat(retrievedTitle).isEqualTo(newTitle)
  }

  @Test fun transactionRetriesAndExhaustsFailures() {
    moviesDatabase.transaction {
      moviesDatabase.moviesQueries.createMovie(id, title)
    }

    assertThrows<OptimisticLockException> {
      moviesDatabase.transaction {
        moviesDatabase.moviesQueries.updateMovie(newTitle, Movies.Version(-3L), id)
      }
    }


    val retrievedTitle = moviesDatabase.transactionWithResult {
      moviesDatabase.moviesQueries.getMovie(id).executeAsOne().title
    }

    assertThat(retrievedTitle).isEqualTo(title)
  }

  @Test fun transactionIgnoresNonRetriableExceptions() {
    moviesDatabase.transaction {
      moviesDatabase.moviesQueries.createMovie(id, title)
    }
    var attempts = 0
    assertThrows<NonRetriableException> {
      moviesDatabase.transaction {
        attempts += 1
        moviesDatabase.moviesQueries.updateMovie(newTitle, Movies.Version(0), id)
        throw NonRetriableException()
      }
    }
    assertThat(attempts).isEqualTo(1)

    val retrievedTitle = moviesDatabase.transactionWithResult {
      moviesDatabase.moviesQueries.getMovie(id).executeAsOne().title
    }

    assertThat(retrievedTitle).isEqualTo(title)
  }

  @Test fun transactionWithResultRetriesAndSucceeds() {
    moviesDatabase.transaction {
      moviesDatabase.moviesQueries.createMovie(id, title)
    }

    var version = -2L

    val updatedTitle = moviesDatabase.transactionWithResult {
      version += 1
      moviesDatabase.moviesQueries.updateMovie(newTitle, Movies.Version(version), id)
      moviesDatabase.moviesQueries.getMovie(id).executeAsOne().title
    }
    assertThat(updatedTitle).isEqualTo(newTitle)

    val retrievedTitle = moviesDatabase.transactionWithResult {
      moviesDatabase.moviesQueries.getMovie(id).executeAsOne().title
    }

    assertThat(retrievedTitle).isEqualTo(newTitle)
  }

  @Test fun transactionWithResultExhaustsFailures() {
    moviesDatabase.transaction {
      moviesDatabase.moviesQueries.createMovie(id, title)
    }

    assertThrows<OptimisticLockException> {
      moviesDatabase.transactionWithResult {
        moviesDatabase.moviesQueries.updateMovie(newTitle, Movies.Version(-3L), id)
      }
    }


    val retrievedTitle = moviesDatabase.transactionWithResult {
      moviesDatabase.moviesQueries.getMovie(id).executeAsOne().title
    }

    assertThat(retrievedTitle).isEqualTo(title)
  }

  @Test fun transactionWithResultIgnoresNonRetriableExceptions() {
    moviesDatabase.transaction {
      moviesDatabase.moviesQueries.createMovie(id, title)
    }
    var attempts = 0
    assertThrows<NonRetriableException> {
      moviesDatabase.transactionWithResult {
        attempts += 1
        moviesDatabase.moviesQueries.updateMovie(newTitle, Movies.Version(0), id)
        throw NonRetriableException()
      }
    }
    assertThat(attempts).isEqualTo(1)

    val retrievedTitle = moviesDatabase.transactionWithResult {
      moviesDatabase.moviesQueries.getMovie(id).executeAsOne().title
    }

    assertThat(retrievedTitle).isEqualTo(title)
  }

  @Test fun nestedRetries() {
    var tries = 0
    assertThrows<OptimisticLockException> {
      moviesDatabase.transaction {
        moviesDatabase.transaction {
          tries++
          throw OptimisticLockException("fake transient exception")
        }
      }
    }

    assertThat(tries).isEqualTo(3)
  }
}
