package misk.hibernate.vitess

import jakarta.inject.Inject
import misk.hibernate.DbMovie
import misk.hibernate.Id
import misk.hibernate.Movies
import misk.hibernate.MoviesTestModule
import misk.hibernate.Transacter
import misk.hibernate.allowCowrites
import misk.hibernate.load
import misk.jdbc.DataSourceType
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.vitess.Keyspace
import misk.vitess.Shard
import misk.vitess.CowriteException
import misk.vitess.testing.TransactionMode
import misk.vitess.testing.utilities.DockerVitess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Test suite that verifies that cross-shard writes are rejected in SINGLE transaction mode and can
 * be opted in via `SET transaction_mode = 'multi'` on the session.
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

    assertThat(exception.stackTraceToString()).contains("CowriteException")
    assertThat(exception.stackTraceToString()).contains("multi-db transaction attempted")
  }

  @Test
  fun `cross-shard write succeeds after opting in via SET transaction_mode`() {
    transacter.transaction { session ->
      session.allowCowrites()
      val movieA = session.load<DbMovie>(crossShardIdA)
      val movieB = session.load<DbMovie>(crossShardIdB)
      movieA.name = "Updated A"
      movieB.name = "Updated B"
    }
  }
}
