package misk.jooq

import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceService
import misk.jdbc.DataSourceType
import misk.jdbc.DatabasePool
import misk.jdbc.PostCommitHookFailedException
import misk.jooq.JooqTransacter.Companion.noRetriesOptions
import misk.jooq.config.ClientJooqTestingModule
import misk.jooq.config.DeleteOrUpdateWithoutWhereException
import misk.jooq.config.JooqDBIdentifier
import misk.jooq.config.JooqDBReadOnlyIdentifier
import misk.jooq.listeners.AvoidUsingSelectStarException
import misk.jooq.listeners.JooqTimestampRecordListenerOptions
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import java.sql.SQLException
import java.sql.SQLRecoverableException
import java.sql.SQLTransientException
import java.time.Clock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import jakarta.inject.Inject
import org.jooq.exception.DataChangedException
import misk.jooq.JooqTransacter.TransacterOptions
import misk.jooq.testgen.tables.records.MovieRecord

@MiskTest(startService = true)
internal class JooqTransacterTest {
  @MiskTestModule private var module = ClientJooqTestingModule()
  @Inject @JooqDBIdentifier private lateinit var transacter: JooqTransacter
  @Inject @JooqDBReadOnlyIdentifier private lateinit var readTransacter: JooqTransacter
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
        }.also { it.store() }
      }

      transacter.transaction(noRetriesOptions) { (ctx) ->
        ctx.selectFrom(MOVIE).where(MOVIE.ID.eq(movie.id)).fetchOne().getOrThrow()
          .apply {
            this.genre = Genre.HORROR.name
          }.also {
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
        }.also { it.store() }
      }
    }
    val numberOfRecords = transacter.transaction(noRetriesOptions) { (ctx) ->
      ctx.selectCount().from(MOVIE).fetchOne()!!.component1()
    }
    assertThat(numberOfRecords).isEqualTo(1)
  }

  @Test
  fun `rollback hooks are called on rollback only`() {
    val rollbackHooksTriggered = mutableListOf<String>()

    // Happy path.
    transacter.transaction { session ->
      session.onRollback { error ->
        rollbackHooksTriggered.add("never")
        error("this should never have happened")
      }
    }

    assertThat(rollbackHooksTriggered).isEmpty()

    // Rollback path.
    assertThrows<IllegalStateException> {
      transacter.transaction { session ->
        session.onRollback { error ->
          assertThat(error).hasMessage("bad things happened here")
          rollbackHooksTriggered.add("first")
        }
        session.onRollback { error ->
          assertThat(error).hasMessage("bad things happened here")
          rollbackHooksTriggered.add("second")
        }
        error("bad things happened here")
      }
    }
    assertThat(rollbackHooksTriggered).containsExactly("first", "second")
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
        }.also { it.store() }
      }
    }
    assertThat(sessionCloseHook2Called).isTrue
  }

  @Test fun `make sure read and write transacters connect to different dbs`() {
    transacter.transaction(noRetriesOptions) {
      transacter.transaction(noRetriesOptions) { (ctx) ->
        ctx.newRecord(MOVIE).apply {
          this.genre = Genre.COMEDY.name
          this.name = "Dumb and dumber"
        }.also { it.store() }
      }
    }

    /**
     * Since no migrations are run on the reader database, this will throw an exception
     * saying Table 'misk_jooq_testing_reader.movie' doesn't exist
     */
    assertThatExceptionOfType(DataAccessException::class.java).isThrownBy {
      readTransacter.transaction { (ctx) ->
        ctx.selectCount().from(MOVIE).fetch { it.component1() }
      }
    }
  }

  @Test fun `delete without where throws an error`() {
    transacter.transaction { (ctx) ->
      ctx.newRecord(MOVIE).apply {
        this.genre = Genre.COMEDY.name
        this.name = "Dumb and dumber"
      }.also { it.store() }
    }
    assertThatExceptionOfType(DeleteOrUpdateWithoutWhereException::class.java).isThrownBy {
      transacter.transaction { (ctx) ->
        // this is expected to throw an error because of the [DeleteOrUpdateWithoutWhereListener]
        // registered
        ctx.deleteFrom(MOVIE).execute()
      }
    }
  }

  @Test fun `using select star throws an exception`() {
    assertThatExceptionOfType(AvoidUsingSelectStarException::class.java).isThrownBy {
      transacter.transaction { (ctx) ->
        ctx.newRecord(MOVIE).apply {
          this.genre = Genre.COMEDY.name
          this.name = "Dumb and dumber"
        }.also { it.store() }

        ctx.select(MOVIE.asterisk()).from(MOVIE).fetchOne()
      }
    }
  }

  @Nested
  inner class IsolationLevelTests {

    @Nested
    inner class CheckIsolationLevel {
      @Test fun `check isolation level is set to repeatable read by default`() {
        transacter.transaction { (ctx) ->
          ctx.connection {
            assertThat(it.transactionIsolation).isEqualTo(TransactionIsolationLevel.REPEATABLE_READ.value)
          }
        }
      }

      @Test fun `check isolation level is set to read committed when explicitly set`() {
        transacter.transaction(
          options = TransacterOptions(isolationLevel = TransactionIsolationLevel.READ_COMMITTED)
        ) { (ctx) ->
          ctx.connection {
            assertThat(it.transactionIsolation).isEqualTo(TransactionIsolationLevel.READ_COMMITTED.value)
          }
        }
      }
    }

    @Nested
    inner class EnsureIsolationLevelsWorkCorrectly {
      lateinit var savedMovieRecord: MovieRecord
      @BeforeEach
      fun insertRecord() {
        savedMovieRecord = transacter.transaction { (ctx) ->
          ctx.newRecord(MOVIE).apply {
            this.genre = Genre.COMEDY.name
            this.name = "Dumb and dumber"
          }.also { it.store() }
        }
      }

      @Test fun `should see the same record as previously read - repeatable read`() {
        transacter.transaction(
          options = TransacterOptions(
            isolationLevel = TransactionIsolationLevel.REPEATABLE_READ // this is default
          )
        ) { (ctx) ->
          var movie = ctx.selectFrom(MOVIE).where(MOVIE.ID.eq(savedMovieRecord.id)).fetchOne()!!
          assertThat(movie.genre).isEqualTo(Genre.COMEDY.name)

          transacter.transaction { (ctx) ->
            ctx.selectFrom(MOVIE).where(MOVIE.ID.eq(savedMovieRecord.id))
              .fetchOne()
              ?.apply { this.genre = Genre.HORROR.name }
              ?.also { it.store() }
          }

          movie = ctx.selectFrom(MOVIE).where(MOVIE.ID.eq(savedMovieRecord.id)).fetchOne()!!
          assertThat(movie.genre).isEqualTo(Genre.COMEDY.name)
        }
      }

      @Test fun `should see the updated record - read committed`() {
        transacter.transaction(
          options = TransacterOptions(
            isolationLevel = TransactionIsolationLevel.READ_COMMITTED
          )
        ) { (ctx) ->
          var movie = ctx.selectFrom(MOVIE).where(MOVIE.ID.eq(savedMovieRecord.id)).fetchOne()!!
          assertThat(movie.genre).isEqualTo(Genre.COMEDY.name)

          transacter.transaction { (ctx) ->
            ctx.selectFrom(MOVIE).where(MOVIE.ID.eq(savedMovieRecord.id))
              .fetchOne()
              ?.apply { this.genre = Genre.HORROR.name }
              ?.also { it.store() }
          }

          movie = ctx.selectFrom(MOVIE).where(MOVIE.ID.eq(savedMovieRecord.id)).fetchOne()!!
          assertThat(movie.genre).isEqualTo(Genre.HORROR.name)
        }
      }

      @Test fun `should see the updated record - read uncommitted`() {
        transacter.transaction(
          options = TransacterOptions(
            isolationLevel = TransactionIsolationLevel.READ_UNCOMMITTED
          )
        ) { (ctx) ->
          var movie = ctx.selectFrom(MOVIE).where(MOVIE.ID.eq(savedMovieRecord.id)).fetchOne()!!
          assertThat(movie.genre).isEqualTo(Genre.COMEDY.name)
          val context1 = ctx

          transacter.transaction { (ctx) ->
            ctx.selectFrom(MOVIE).where(MOVIE.ID.eq(savedMovieRecord.id))
              .fetchOne()
              ?.apply { this.genre = Genre.HORROR.name }
              ?.also { it.store() }

            // txn is still un-committed here
            movie = context1.selectFrom(MOVIE).where(MOVIE.ID.eq(savedMovieRecord.id)).fetchOne()!!
            assertThat(movie.genre).isEqualTo(Genre.HORROR.name)
          }
        }
      }
    }
  }

  @Nested
  inner class ExceptionHandlingTests {

    // Note: CockroachDB support test removed due to test infrastructure limitations

    @Test fun `retries on SQLRecoverableException`() {
      var numberOfRetries = 0
      assertThatExceptionOfType(DataAccessException::class.java).isThrownBy {
        transacter.transaction {
          numberOfRetries++
          throw SQLRecoverableException("Connection lost")
        }
      }
      assertThat(numberOfRetries).isEqualTo(3)
    }

    @Test fun `retries on SQLTransientException`() {
      var numberOfRetries = 0
      assertThatExceptionOfType(DataAccessException::class.java).isThrownBy {
        transacter.transaction {
          numberOfRetries++
          throw SQLTransientException("Temporary failure")
        }
      }
      assertThat(numberOfRetries).isEqualTo(3)
    }

    @Test fun `retries on DataChangedException`() {
      var numberOfRetries = 0
      assertThatExceptionOfType(DataChangedException::class.java).isThrownBy {
        transacter.transaction {
          numberOfRetries++
          throw DataChangedException("Optimistic lock failed")
        }
      }
      assertThat(numberOfRetries).isEqualTo(3)
    }

    @Test fun `retries on DataAccessException with retryable SQLException cause`() {
      var numberOfRetries = 0
      assertThatExceptionOfType(DataAccessException::class.java).isThrownBy {
        transacter.transaction {
          numberOfRetries++
          val sqlException = SQLException("Connection is closed")
          throw DataAccessException("JOOQ error", sqlException)
        }
      }
      assertThat(numberOfRetries).isEqualTo(3)
    }

    @Test fun `retries on SQLException with connection closed message`() {
      var numberOfRetries = 0
      assertThatExceptionOfType(DataAccessException::class.java).isThrownBy {
        transacter.transaction {
          numberOfRetries++
          throw SQLException("Connection is closed")
        }
      }
      assertThat(numberOfRetries).isEqualTo(3)
    }

    @Test fun `retries on SQLException with Vitess transaction not found message`() {
      var numberOfRetries = 0
      assertThatExceptionOfType(DataAccessException::class.java).isThrownBy {
        transacter.transaction {
          numberOfRetries++
          throw SQLException("vttablet: rpc error: code = Aborted desc = transaction 1572922696317821557: not found (CallerID: )")
        }
      }
      assertThat(numberOfRetries).isEqualTo(3)
    }

    @Test fun `retries on SQLException with TiDB write conflict`() {
      var numberOfRetries = 0
      assertThatExceptionOfType(DataAccessException::class.java).isThrownBy {
        transacter.transaction {
          numberOfRetries++
          val sqlException = SQLException("Write conflict", "HY000", 9007)
          throw sqlException
        }
      }
      assertThat(numberOfRetries).isEqualTo(3)
    }

    @Test fun `does not retry on non-retryable SQLException`() {
      var numberOfRetries = 0
      assertThatExceptionOfType(DataAccessException::class.java).isThrownBy {
        transacter.transaction {
          numberOfRetries++
          throw SQLException("Syntax error", "42000", 1064)
        }
      }
      // Non-retryable SQLException should not retry
      assertThat(numberOfRetries).isEqualTo(1)
    }

    @Test fun `does not retry on DataAccessException with non-retryable cause`() {
      var numberOfRetries = 0
      assertThatExceptionOfType(DataAccessException::class.java).isThrownBy {
        transacter.transaction {
          numberOfRetries++
          val sqlException = SQLException("Syntax error", "42000", 1064)
          throw DataAccessException("JOOQ error", sqlException)
        }
      }
      // DataAccessException with non-retryable SQLException cause should not retry
      assertThat(numberOfRetries).isEqualTo(1)
    }

    @Test fun `retries on nested retryable exception in cause chain`() {
      var numberOfRetries = 0
      assertThatExceptionOfType(DataAccessException::class.java).isThrownBy {
        transacter.transaction {
          numberOfRetries++
          val rootCause = SQLRecoverableException("Connection lost")
          val intermediateCause = RuntimeException("Intermediate", rootCause)
          throw DataAccessException("JOOQ error", intermediateCause)
        }
      }
      assertThat(numberOfRetries).isEqualTo(3)
    }

    @Test fun `succeeds after retryable exception on second attempt`() {
      var numberOfRetries = 0
      var result: String? = null

      assertThatCode {
        result = transacter.transaction {
          numberOfRetries++
          if (numberOfRetries == 1) {
            throw SQLRecoverableException("Connection lost")
          }
          "success"
        }
      }.doesNotThrowAnyException()

      assertThat(numberOfRetries).isEqualTo(2)
      assertThat(result).isEqualTo("success")
    }
  }
}
