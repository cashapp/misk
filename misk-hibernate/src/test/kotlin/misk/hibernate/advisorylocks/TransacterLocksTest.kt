package misk.hibernate.advisorylocks

import jakarta.inject.Inject
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import misk.hibernate.DbMovie
import misk.hibernate.MovieQuery
import misk.hibernate.Movies
import misk.hibernate.MoviesTestModule
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.jdbc.DataSourceType
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

abstract class TransacterLocksTest {
  @Inject @Movies lateinit var transacter: Transacter

  @Inject lateinit var queryFactory: Query.Factory

  @BeforeEach
  fun setup() {
    createTestData()
  }

  @Test
  fun `cannot take lock while held`() {
    val holdLockLatch = CountDownLatch(1)
    val finishLatch = CountDownLatch(1)
    val holder = Thread {
      transacter.withLock(LOCK_KEY) {
        holdLockLatch.countDown()
        finishLatch.await(1, TimeUnit.SECONDS)
      }
    }
    holder.start()
    holdLockLatch.await(1, TimeUnit.SECONDS)

    assertThrows<IllegalStateException>("Unable to acquire lock $LOCK_KEY") { transacter.withLock(LOCK_KEY) {} }
    finishLatch.countDown()
    holder.join(1000)

    assertThat(holdLockLatch.count).isZero
    assertThat(finishLatch.count).isZero
  }

  @Test
  fun `cannot take nested lock`() {
    transacter.withLock(LOCK_KEY) {
      assertDoesNotThrow {
        transacter.transaction { session ->
          queryFactory.newQuery(MovieQuery::class).name("Star Wars").uniqueResult(session)!!
        }
      }
      assertThrows<IllegalStateException>("nested session") {
        transacter.withLock(LOCK_KEY) {
          transacter.transaction { session -> throw AssertionError("expected unreachable") }
        }
      }
      assertDoesNotThrow {
        transacter.transaction { session ->
          queryFactory.newQuery(MovieQuery::class).name("Jurassic Park").uniqueResult(session)!!
        }
      }
    }
  }

  @Test
  fun `lock is released automatically`() {
    val counter = AtomicInteger()
    transacter.withLock(LOCK_KEY) { counter.incrementAndGet() }
    transacter.withLock(LOCK_KEY) { counter.incrementAndGet() }
    assertThat(counter.get()).isEqualTo(2)
  }

  private fun createTestData() {
    // Insert some movies, characters and actors.
    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9)))
      session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
      session.save(DbMovie("Luxo Jr.", LocalDate.of(1986, 8, 17)))
    }
  }

  companion object {
    private const val LOCK_KEY = "test-lock"
  }
}

@MiskTest(startService = true)
class MySQLTransacterLocksTest : TransacterLocksTest() {
  @MiskTestModule val module = MoviesTestModule(DataSourceType.MYSQL)
}

@MiskTest(startService = true)
class PostgresTransacterLocksTest : TransacterLocksTest() {
  @MiskTestModule val module = MoviesTestModule(DataSourceType.POSTGRESQL)
}

@MiskTest(startService = true)
class VitessTransacterLocksTest : TransacterLocksTest() {
  @MiskTestModule val module = MoviesTestModule(DataSourceType.VITESS_MYSQL)
}
