package misk.sqldelight

import app.cash.sqldelight.db.OptimisticLockException
import misk.sqldelight.testing.Movies
import misk.sqldelight.testing.MoviesDatabase
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.inject.Inject

@MiskTest(startService = true)
class RetryingTransacterTest {
  @MiskTestModule
  val module = SqlDelightTestModule()

  class NonRetriableException() : RuntimeException()

  @Inject @SqlDelightTestdb lateinit var transacter: RetryingTransacter
  @Inject lateinit var moviesDatabase: MoviesDatabase

  private val id = 4L
  private val title = "Fellowship of the Ring"
  private val newTitle = "The Two Towers"

  @Test fun transactionRetriesAndSucceeds() {
    transacter.transaction {
      moviesDatabase.moviesQueries.createMovie(id, title)
    }

    var version = -2L

    transacter.transaction {
      version += 1
      moviesDatabase.moviesQueries.updateMovie(newTitle, Movies.Version(version), id)
    }

    val retrievedTitle = transacter.transactionWithResult {
      moviesDatabase.moviesQueries.getMovie(id).executeAsOne().title
    }

    assertThat(retrievedTitle).isEqualTo(newTitle)
  }

  @Test fun transactionRetriesAndExhaustsFailures() {
    transacter.transaction {
      moviesDatabase.moviesQueries.createMovie(id, title)
    }

    assertThrows<OptimisticLockException> {
      transacter.transaction {
        moviesDatabase.moviesQueries.updateMovie(newTitle, Movies.Version(-3L), id)
      }
    }


    val retrievedTitle = transacter.transactionWithResult {
      moviesDatabase.moviesQueries.getMovie(id).executeAsOne().title
    }

    assertThat(retrievedTitle).isEqualTo(title)
  }

  @Test fun transactionIgnoresNonRetriableExceptions() {
    transacter.transaction {
      moviesDatabase.moviesQueries.createMovie(id, title)
    }
    var attempts = 0
    assertThrows<NonRetriableException> {
      transacter.transaction {
        attempts += 1
        moviesDatabase.moviesQueries.updateMovie(newTitle, Movies.Version(0), id)
        throw NonRetriableException()
      }
    }
    assertThat(attempts).isEqualTo(1)

    val retrievedTitle = transacter.transactionWithResult {
      moviesDatabase.moviesQueries.getMovie(id).executeAsOne().title
    }

    assertThat(retrievedTitle).isEqualTo(title)
  }

  @Test fun transactionWithResultRetriesAndSucceeds() {
    transacter.transaction {
      moviesDatabase.moviesQueries.createMovie(id, title)
    }

    var version = -2L

    val updatedTitle = transacter.transactionWithResult {
      version += 1
      moviesDatabase.moviesQueries.updateMovie(newTitle, Movies.Version(version), id)
      moviesDatabase.moviesQueries.getMovie(id).executeAsOne().title
    }
    assertThat(updatedTitle).isEqualTo(newTitle)

    val retrievedTitle = transacter.transactionWithResult {
      moviesDatabase.moviesQueries.getMovie(id).executeAsOne().title
    }

    assertThat(retrievedTitle).isEqualTo(newTitle)
  }

  @Test fun transactionWithResultExhaustsFailures() {
    transacter.transaction {
      moviesDatabase.moviesQueries.createMovie(id, title)
    }

    assertThrows<OptimisticLockException> {
      transacter.transactionWithResult {
        moviesDatabase.moviesQueries.updateMovie(newTitle, Movies.Version(-3L), id)
      }
    }


    val retrievedTitle = transacter.transactionWithResult {
      moviesDatabase.moviesQueries.getMovie(id).executeAsOne().title
    }

    assertThat(retrievedTitle).isEqualTo(title)
  }

  @Test fun transactionWithResultIgnoresNonRetriableExceptions() {
    transacter.transaction {
      moviesDatabase.moviesQueries.createMovie(id, title)
    }
    var attempts = 0
    assertThrows<NonRetriableException> {
      transacter.transactionWithResult {
        attempts += 1
        moviesDatabase.moviesQueries.updateMovie(newTitle, Movies.Version(0), id)
        throw NonRetriableException()
      }
    }
    assertThat(attempts).isEqualTo(1)

    val retrievedTitle = transacter.transactionWithResult {
      moviesDatabase.moviesQueries.getMovie(id).executeAsOne().title
    }

    assertThat(retrievedTitle).isEqualTo(title)
  }

  @Test fun nestedRetries() {
    var tries = 0
    val oneRetryTransacter = transacter.maxAttempts(2)
    assertThrows<OptimisticLockException> {
      oneRetryTransacter.transaction {
        oneRetryTransacter.transaction {
          tries++
          throw OptimisticLockException("fake transient exception")
        }
      }
    }

    assertThat(tries).isEqualTo(2)
  }
}
