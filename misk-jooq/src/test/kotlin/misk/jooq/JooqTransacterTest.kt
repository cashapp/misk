package misk.jooq

import misk.jdbc.PostCommitHookFailedException
import misk.jooq.JooqTransacter.Companion.noRetriesOptions
import misk.jooq.config.ClientJooqTestingModule
import misk.jooq.config.JooqDBIdentifier
import misk.jooq.model.Genre
import misk.jooq.testgen.tables.references.MOVIE
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.jooq.exception.DataAccessException
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@MiskTest(startService = true)
internal class JooqTransacterTest {
  @MiskTestModule private var module = ClientJooqTestingModule()
  @Inject @JooqDBIdentifier private lateinit var transacter: JooqTransacter
  @Inject private lateinit var clock: FakeClock

  @Test fun `retries to the max number of retries in case of a data access exception`() {
    var numberOfRetries = 0
    assertThatExceptionOfType(DataAccessException::class.java).isThrownBy {
      transacter.transaction {
        numberOfRetries++
        throw DataAccessException("")
      }
    }
    assertThat(numberOfRetries).isEqualTo(3)
  }

  @Test fun `retries and succeeds after the first attempt in case of a data access exception`() {
    var numberOfRetries = 0
    assertThatCode {
      transacter.transaction {
        numberOfRetries++
        // Throw an exception only once
        if (numberOfRetries <= 1) {
          throw DataAccessException("")
        }
      }
    }.doesNotThrowAnyException()
    // It will retry once, so totally it should have executed twice.
    assertThat(numberOfRetries).isEqualTo(2)
  }

  @Test fun `does not retry in case of any other exception except DataAccessException`() {
    var numberOfRetries = 0
    assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy {
      transacter.transaction {
        numberOfRetries++
        throw IllegalStateException("")
      }
    }
    assertThat(numberOfRetries).isEqualTo(1)
  }

  @Test fun `retries and succeeds in case of an optimistic lock exception`() {
    val movie = transacter.transaction { (ctx) ->
      ctx.newRecord(MOVIE).apply {
        this.genre = Genre.COMEDY.name
        this.name = "Enter the dragon"
        createdAt = clock.instant().toLocalDateTime()
        updatedAt = clock.instant().toLocalDateTime()
      }.also { it.store() }
    }
    executeInThreadsAndWait(
      {
        transacter.transaction { (ctx) ->
          ctx.selectFrom(MOVIE).where(MOVIE.ID.eq(movie.id))
            .fetchOne()
            ?.apply { this.name = "The Conjuring" }
            ?.also { it.store() }
        }
      },
      {
        transacter.transaction { (ctx) ->
          ctx.selectFrom(MOVIE).where(MOVIE.ID.eq(movie.id))
            .fetchOne()
            ?.apply { this.genre = Genre.HORROR.name }
            ?.also { it.store() }
        }
      }
    )

    val updatedMovie = transacter.transaction { (ctx) ->
      ctx.selectFrom(MOVIE).where(MOVIE.ID.eq(movie.id))
        .fetchOne()
    }
    assertThat(updatedMovie).isNotNull
    assertThat(updatedMovie!!.genre).isEqualTo(Genre.HORROR.name)
    assertThat(updatedMovie.name).isEqualTo("The Conjuring")
  }

  private fun executeInThreadsAndWait(vararg tasks: () -> Unit) {
    val countDownLatch = CountDownLatch(tasks.size)
    val threads = tasks.map { task ->
      Thread {
        task()
        countDownLatch.countDown()
        try {
          countDownLatch.await(10, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
          throw RuntimeException(e)
        }
      }
    }
    threads.forEach { it.start() }
    threads.forEach { thread -> thread.join(10000) }
  }

  @Test fun `does not retry in case of no exception`() {
    var numberOfRetries = 0
    transacter.transaction {
      numberOfRetries++
    }
    assertThat(numberOfRetries).isEqualTo(1)
  }

  @Test fun `does not retry in a noRetriesTransaction in case of any exception`() {
    var numberOfRetries = 0
    assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy {
      transacter.transaction(noRetriesOptions) {
        numberOfRetries++
        throw IllegalStateException()
      }
    }
    assertThat(numberOfRetries).isEqualTo(1)
  }

  @Test fun `should rollback in case there is an exception thrown`() {
    Assertions.assertThatThrownBy {
      transacter.transaction(noRetriesOptions) { (ctx) ->
        ctx.newRecord(MOVIE).apply {
          this.genre = Genre.COMEDY.name
          this.name = "Dumb and dumber"
          createdAt = clock.instant().toLocalDateTime()
          updatedAt = clock.instant().toLocalDateTime()
        }.also { it.store() }
        throw IllegalStateException("") // to force a rollback
      }
    }

    val numberOfRecords = transacter.transaction(noRetriesOptions) { (ctx) ->
      ctx.selectCount().from(MOVIE).fetchOne()!!.component1()
    }
    assertThat(numberOfRecords).isEqualTo(0)
  }

  /**
   * The way the code works is that, every time we call transacter.transaction { } it
   * gets a new connection from the connection pool. So even though it looks like a
   * nested transaction, it's not. The inner transaction works on a new connection and
   * will commit and rollback based on what happens in its boundary.
   * This test is to prove that this works.
   */
  @Test fun `nested transactions work as they are not really nested transactions`() {
    transacter.transaction(noRetriesOptions) {
      val movie = transacter.transaction(noRetriesOptions) { (ctx) ->
        ctx.newRecord(MOVIE).apply {
          this.genre = Genre.COMEDY.name
          this.name = "Dumb and dumber"
          createdAt = clock.instant().toLocalDateTime()
          updatedAt = clock.instant().toLocalDateTime()
        }.also { it.store() }
      }

      transacter.transaction(noRetriesOptions) { (ctx) ->
        ctx.selectFrom(MOVIE).where(MOVIE.ID.eq(movie.id)).fetchOne().getOrThrow()
          .apply {
            this.genre = Genre.HORROR.name
          }
          .also {
            it.updatedAt = clock.instant().toLocalDateTime()
          }
          .also {
            it.store()
          }
      }

      val updatedMovie = transacter.transaction { (ctx) ->
        ctx.selectFrom(MOVIE).where(MOVIE.ID.eq(movie.id))
          .fetchOne()
      }

      assertThat(updatedMovie!!.genre).isEqualTo(Genre.HORROR.name)
    }
  }

  @Test fun `pre commit hooks execute`() {
    var preCommitHook1Executed = false
    var preCommitHook2Executed = false
    transacter.transaction { session ->
      session.onPreCommit {
        preCommitHook1Executed = true
      }
      session.onPreCommit {
        preCommitHook2Executed = true
      }
    }
    assertThat(preCommitHook1Executed).isTrue
    assertThat(preCommitHook2Executed).isTrue
  }

  @Test fun `an exception during a pre commit hook rolls back the transaction`() {
    assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
      transacter.transaction { session ->
        session.onPreCommit {
          throw RuntimeException()
        }
        session.ctx.newRecord(MOVIE).apply {
          this.genre = Genre.COMEDY.name
          this.name = "Dumb and dumber"
          createdAt = clock.instant().toLocalDateTime()
          updatedAt = clock.instant().toLocalDateTime()
        }.also { it.store() }
      }
    }
    val numberOfRecords = transacter.transaction(noRetriesOptions) { (ctx) ->
      ctx.selectCount().from(MOVIE).fetchOne()!!.component1()
    }
    assertThat(numberOfRecords).isEqualTo(0)
  }

  @Test fun `post commit hooks execute`() {
    var postCommitHook1Executed = false
    var postCommitHook2Executed = false
    transacter.transaction { session ->
      session.onPostCommit {
        postCommitHook1Executed = true
      }
      session.onPostCommit {
        postCommitHook2Executed = true
      }
    }
    assertThat(postCommitHook1Executed).isTrue
    assertThat(postCommitHook2Executed).isTrue
  }

  @Test fun `an exception in a post commit does not rollback the transaction`() {
    assertThatExceptionOfType(PostCommitHookFailedException::class.java).isThrownBy {
      transacter.transaction { session ->
        session.onPostCommit {
          throw RuntimeException()
        }
        session.ctx.newRecord(MOVIE).apply {
          this.genre = Genre.COMEDY.name
          this.name = "Dumb and dumber"
          createdAt = clock.instant().toLocalDateTime()
          updatedAt = clock.instant().toLocalDateTime()
        }.also { it.store() }
      }
    }
    val numberOfRecords = transacter.transaction(noRetriesOptions) { (ctx) ->
      ctx.selectCount().from(MOVIE).fetchOne()!!.component1()
    }
    assertThat(numberOfRecords).isEqualTo(1)
  }

  @Test fun `session close hooks always execute regardless of exceptions thrown from anywhere`() {
    var sessionCloseHook1Called = false
    assertThatExceptionOfType(PostCommitHookFailedException::class.java).isThrownBy {
      transacter.transaction { session ->
        session.onPostCommit {
          throw RuntimeException()
        }
        session.onSessionClose {
          sessionCloseHook1Called = true
        }
      }
    }
    assertThat(sessionCloseHook1Called).isTrue

    var sessionCloseHook2Called = false
    assertThatExceptionOfType(DataAccessException::class.java).isThrownBy {
      transacter.transaction(noRetriesOptions) { session ->
        session.onSessionClose {
          sessionCloseHook2Called = true
        }

        session.ctx.newRecord(MOVIE).apply {
          this.genre = Genre.COMEDY.name
          this.name = null // to force a sql exception
          createdAt = clock.instant().toLocalDateTime()
          updatedAt = clock.instant().toLocalDateTime()
        }.also { it.store() }
      }
    }
    assertThat(sessionCloseHook2Called).isTrue
  }
}
