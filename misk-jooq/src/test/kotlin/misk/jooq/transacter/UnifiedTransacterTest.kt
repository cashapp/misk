package misk.jooq.transacter

import jakarta.inject.Inject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import misk.jooq.TransactionIsolationLevel
import misk.jooq.config.ClientJooqTestingModule
import misk.jooq.model.Genre
import misk.jooq.testgen.tables.references.MOVIE
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.jooq.exception.DataAccessException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@MiskTest(startService = true)
class UnifiedTransacterTest {
  @MiskTestModule @Suppress("unused") private var module = ClientJooqTestingModule()
  @Inject private lateinit var transacter: Transacter

  @Test
  fun `nested transactions throw`() {
    val exception = assertThrows<IllegalStateException> { transacter.transaction { transacter.transaction {} } }
    assertThat(exception.message).isEqualTo("Nested transactions are not currently supported")
  }

  @Test
  fun `retries respects max attempts`() {
    var attempts = 0
    assertThrows<DataAccessException> {
      transacter.maxAttempts(2).transaction {
        attempts++
        throw DataAccessException("boom")
      }
    }
    assertThat(attempts).isEqualTo(2)
  }

  @Test
  fun `read only transaction rejects writes`() {
    assertThrows<DataAccessException> {
      transacter.readOnly().transaction { (ctx) ->
        ctx
          .newRecord(MOVIE)
          .apply {
            name = "Read Only Should Fail"
            genre = Genre.COMEDY.name
          }
          .also { it.store() }
      }
    }
  }

  @Test
  fun `read only setting reset on subsequent transaction`() {
    transacter.readOnly().transaction { (ctx) -> ctx.selectFrom(MOVIE).fetch() }

    val record =
      transacter.transaction { (ctx) ->
        ctx
          .newRecord(MOVIE)
          .apply {
            name = "Writable After Read Only"
            genre = Genre.HORROR.name
          }
          .also { it.store() }
      }

    assertThat(record.id).isNotNull
  }

  @Test
  fun `replicaRead uses the reader datasource`() {
    val writerDatabase = transacter.transaction { session -> session.useConnection { it.catalog } }
    val readerDatabase = transacter.replicaRead { session -> session.useConnection { it.catalog } }

    // This is an effective test because the testing module uses a separate database for the read data source. This
    // would not normally be the case, but it allows us to easily verify the replicaRead actually uses the reader
    // data source
    assertThat(writerDatabase).isEqualTo("misk_jooq_testing_writer")
    assertThat(readerDatabase).isEqualTo("misk_jooq_testing_reader")
  }

  @Test
  fun `inTransaction reflects transaction state`() {
    assertThat(transacter.inTransaction).isFalse()

    transacter.transaction { assertThat(transacter.inTransaction).isTrue() }

    assertThat(transacter.inTransaction).isFalse()
  }

  @Test
  fun `inTransaction is thread-local and correct across concurrent transactions`() {
    val threadCount = 5
    val executor = Executors.newFixedThreadPool(threadCount)
    val parallelThreadHasTxn = CountDownLatch(1)
    val allThreadsStarted = CountDownLatch(threadCount)
    val allThreadsInsideTransaction = CountDownLatch(threadCount)
    val allThreadsCanFinish = CountDownLatch(1)
    val errors = ConcurrentLinkedQueue<Throwable>()

    // Run another thread holding a txn / connection the entire time
    val parallelTxn =
      Thread {
          runCatching {
              assertThat(transacter.inTransaction).isFalse()
              transacter.transaction {
                parallelThreadHasTxn.countDown()
                assertThat(transacter.inTransaction).isTrue()
                allThreadsCanFinish.await(10, TimeUnit.SECONDS)
              }
              assertThat(transacter.inTransaction).isFalse()
            }
            .onFailure { errors.add(it) }
        }
        .also { it.start() }

    assertThat(transacter.inTransaction).isFalse()
    repeat(threadCount) {
      executor.submit {
        runCatching {
            // Wait for another txn to already be open
            parallelThreadHasTxn.await(10, TimeUnit.SECONDS)
            // This thread should still not think it's in a transaction
            assertThat(transacter.inTransaction).isFalse()
            allThreadsStarted.countDown()

            transacter.transaction {
              assertThat(transacter.inTransaction).isTrue()
              allThreadsInsideTransaction.countDown()
              // Wait for all threads to enter a transaction
              allThreadsCanFinish.await(10, TimeUnit.SECONDS)
              assertThat(transacter.inTransaction).isTrue()
            }

            assertThat(transacter.inTransaction).isFalse()
          }
          .onFailure { errors.add(it) }
      }
    }

    allThreadsInsideTransaction.await(10, TimeUnit.SECONDS)
    allThreadsCanFinish.countDown()
    parallelTxn.join(10.seconds.inWholeMilliseconds)
    executor.shutdown()
    executor.awaitTermination(30, TimeUnit.SECONDS)

    assertThat(errors).isEmpty()
  }

  @Test
  fun `transacter options are thread-local`() {
    val realTransacter = transacter as RealTransacter
    val readOnlyThreadReady = CountDownLatch(1)
    val writableThreadReady = CountDownLatch(1)
    val bothThreadsCanProceed = CountDownLatch(1)
    val errors = ConcurrentLinkedQueue<Throwable>()

    val readOnlyThread =
      Thread {
          runCatching {
              transacter.readOnly().transaction { (ctx) ->
                readOnlyThreadReady.countDown()
                bothThreadsCanProceed.await(10, TimeUnit.SECONDS)

                assertThat(realTransacter.options.readOnly).isTrue()

                assertThrows<DataAccessException> {
                  ctx.newRecord(MOVIE).apply {
                    name = "Should Fail"
                    genre = Genre.COMEDY.name
                    insert()
                  }
                }
              }
            }
            .onFailure { errors.add(it) }
        }
        .also { it.start() }

    val writableThread =
      Thread {
          runCatching {
              readOnlyThreadReady.await(10, TimeUnit.SECONDS)

              transacter.transaction { (ctx) ->
                writableThreadReady.countDown()
                bothThreadsCanProceed.await(10, TimeUnit.SECONDS)

                assertThat(realTransacter.options.readOnly).isFalse()

                val record =
                  ctx.newRecord(MOVIE).apply {
                    name = "Writable Thread Movie"
                    genre = Genre.HORROR.name
                    insert()
                  }
                assertThat(record.id).isNotNull()
              }
            }
            .onFailure { errors.add(it) }
        }
        .also { it.start() }

    readOnlyThreadReady.await(10, TimeUnit.SECONDS)
    writableThreadReady.await(10, TimeUnit.SECONDS)
    bothThreadsCanProceed.countDown()

    readOnlyThread.join(10.seconds.inWholeMilliseconds)
    writableThread.join(10.seconds.inWholeMilliseconds)

    assertThat(errors).isEmpty()
  }

  @Test
  fun `transacter options are cumulative`() {
    val realTransacter = transacter as RealTransacter

    transacter
      .readOnly()
      .maxAttempts(5)
      .maxRetryDelay(2.seconds.toJavaDuration())
      .isolationLevel(TransactionIsolationLevel.SERIALIZABLE)
      .transaction {
        val options = realTransacter.options
        assertThat(options.readOnly).isTrue()
        assertThat(options.maxAttempts).isEqualTo(5)
        assertThat(options.maxRetryDelayMillis).isEqualTo(2000)
        assertThat(options.isolationLevel).isEqualTo(TransactionIsolationLevel.SERIALIZABLE)
      }
  }
}
