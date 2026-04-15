package misk.hibernate.vitess

import jakarta.inject.Inject
import misk.hibernate.DbMovie
import misk.hibernate.Id
import misk.hibernate.MovieQuery
import misk.hibernate.Movies
import misk.hibernate.MoviesTestModule
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.allowCrossShardTransactions
import misk.hibernate.allowScatter
import misk.hibernate.load
import misk.hibernate.newQuery
import misk.jdbc.DataSourceType
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.vitess.CrossShardTransactionException
import misk.vitess.Keyspace
import misk.vitess.Shard
import misk.vitess.testing.TransactionMode
import misk.vitess.testing.utilities.DockerVitess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Test suite that verifies that cross-shard transactions (reads and writes) are rejected in SINGLE
 * transaction mode and can be opted in via [allowCrossShardTransactions].
 */
@MiskTest(startService = true)
class VitessTransactionModeIntegrationTest {
  @MiskExternalDependency
  private val dockerVitess =
    DockerVitess(
      containerName = "vitess_txn_mode_integ_test_db",
      transactionMode = TransactionMode.SINGLE,
      port = 29303,
    )

  @MiskTestModule
  val module = MoviesTestModule(type = DataSourceType.VITESS_MYSQL, singleTransactionMode = true)

  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  private lateinit var crossShardIdA: Id<DbMovie>
  private lateinit var crossShardIdB: Id<DbMovie>

  @BeforeEach
  fun setUp() {
    val keyspace = Keyspace("movies_sharded")
    val shard1 = Shard(keyspace, "-80")
    val shard2 = Shard(keyspace, "80-")

    // Insert movies one at a time in separate transactions (each is single-shard, so succeeds).
    val movieIds = (1..10).map { i ->
      transacter.transaction { session -> session.save(DbMovie("Movie $i")) }
    }

    // Partition IDs by shard and pick one from each.
    val byShard = movieIds.groupBy { id -> if (shard1.contains(Shard.Key.hash(id.id))) shard1 else shard2 }
    assertThat(byShard.keys).hasSize(2)
      .withFailMessage("All movie IDs landed on the same shard: $movieIds")
    crossShardIdA = byShard[shard1]!!.first()
    crossShardIdB = byShard[shard2]!!.first()
  }

  @Test
  fun `single-shard write with auto-increment sequence succeeds in SINGLE transaction mode`() {
    // Auto-increment sequence allocation is autocommit, so a single insert doesn't cross shards.
    transacter.transaction { session -> session.save(DbMovie("Single Shard Movie")) }
  }

  @Test
  fun `cross-shard read fails with SINGLE transaction mode`() {
    // A scatter query reads from all shards in one transaction, which SINGLE mode blocks.
    val exception = assertThrows<Exception> {
      transacter.transaction { session ->
        queryFactory.newQuery<MovieQuery>().allowScatter().list(session)
      }
    }

    assertThat(generateSequence(exception as Throwable) { it.cause }.any { it is CrossShardTransactionException })
      .isTrue()
  }

  @Test
  fun `cross-shard write fails with SINGLE transaction mode`() {
    val exception =
      assertThrows<Exception> {
        transacter.transaction { session ->
          val movieA = session.load<DbMovie>(crossShardIdA)
          val movieB = session.load<DbMovie>(crossShardIdB)
          movieA.name = "Updated A"
          movieB.name = "Updated B"
        }
      }

    assertThat(generateSequence(exception as Throwable) { it.cause }.any { it is CrossShardTransactionException })
      .isTrue()
  }

  @Test
  fun `cross-shard read and write in same transaction fails with SINGLE transaction mode`() {
    val exception = assertThrows<Exception> {
      transacter.transaction { session ->
        // Read from one shard, write to another.
        val movieA = session.load<DbMovie>(crossShardIdA)
        val movieB = session.load<DbMovie>(crossShardIdB)
        movieB.name = "Updated from ${movieA.name}"
      }
    }

    assertThat(generateSequence(exception as Throwable) { it.cause }.any { it is CrossShardTransactionException })
      .isTrue()
  }

  @Test
  fun `cross-shard transactions succeed after opting in via allowCrossShardTransactions`() {
    transacter.transaction { session ->
      session.allowCrossShardTransactions()
      val movieA = session.load<DbMovie>(crossShardIdA)
      val movieB = session.load<DbMovie>(crossShardIdB)
      movieA.name = "Updated A"
      movieB.name = "Updated B"
    }
  }

  @Test
  fun `allowCrossShardTransactions does not leak to subsequent transactions via connection pool`() {
    // The test data source is configured with fixed_pool_size=1 so the second transaction is
    // guaranteed to reuse the same connection, making this test deterministic.

    // First: opt in and do a cross-shard write.
    transacter.transaction { session ->
      session.allowCrossShardTransactions()
      val movieA = session.load<DbMovie>(crossShardIdA)
      val movieB = session.load<DbMovie>(crossShardIdB)
      movieA.name = "Opted In A"
      movieB.name = "Opted In B"
    }

    // Second: without opt-in, a cross-shard write should still fail.
    // If this passes (doesn't throw), the session variable leaked through the connection pool.
    val exception = assertThrows<Exception> {
      transacter.transaction { session ->
        val movieA = session.load<DbMovie>(crossShardIdA)
        val movieB = session.load<DbMovie>(crossShardIdB)
        movieA.name = "Should Fail A"
        movieB.name = "Should Fail B"
      }
    }

    assertThat(generateSequence(exception as Throwable) { it.cause }.any { it is CrossShardTransactionException })
      .isTrue()
  }

  @Test
  fun `onSessionClose hooks can use cross-shard transactions after allowCrossShardTransactions`() {
    // Verifies that after a transaction with allowCrossShardTransactions() commits, onSessionClose
    // hooks that reuse the thread-local session can still perform cross-shard reads. Without the
    // afterCompletion re-set to MULTI, the session reverts to SINGLE mode after commit, and the
    // onSessionClose hook's cross-shard read fails with stale reserved connection shard sessions.
    var onSessionCloseSucceeded = false

    transacter.transaction { session ->
      session.allowCrossShardTransactions()

      // Do a cross-shard write.
      val movieA = session.load<DbMovie>(crossShardIdA)
      val movieB = session.load<DbMovie>(crossShardIdB)
      movieA.name = "Hook Test A"
      movieB.name = "Hook Test B"

      // Register onSessionClose hook that does a cross-shard read.
      session.onSessionClose {
        transacter.readOnly().transaction { readSession ->
          queryFactory.newQuery<MovieQuery>().allowScatter().list(readSession)
          onSessionCloseSucceeded = true
        }
      }
    }

    assertThat(onSessionCloseSucceeded)
      .withFailMessage("onSessionClose hook should succeed with cross-shard read after allowCrossShardTransactions")
      .isTrue()
  }
}
