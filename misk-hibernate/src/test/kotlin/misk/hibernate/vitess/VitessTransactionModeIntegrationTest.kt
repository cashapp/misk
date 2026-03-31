package misk.hibernate.vitess

import jakarta.inject.Inject
import javax.persistence.PersistenceException
import misk.hibernate.DbActor
import misk.hibernate.DbMovie
import misk.hibernate.Movies
import misk.hibernate.MoviesTestModule
import misk.hibernate.Transacter
import misk.jdbc.DataSourceType
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.vitess.testing.TransactionMode
import misk.vitess.testing.utilities.DockerVitess
import org.assertj.core.api.Assertions.assertThat
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

  @Test
  fun `cross-shard write fails with SINGLE transaction mode`() {
    // Save enough entities in one transaction that it's virtually certain they span multiple shards.
    // With a hash vindex on 2 shards, 10 entities will almost certainly land on both shards.
    val exception =
      assertThrows<PersistenceException> {
        transacter.transaction { session ->
          for (i in 1..10) {
            session.save(DbMovie("Movie $i"))
          }
        }
      }

    assertThat(exception.stackTraceToString()).contains("multi-db transaction attempted")
  }

  @Test
  fun `cross-shard write succeeds after opting in via SET transaction_mode`() {
    transacter.transaction { session ->
      session.hibernateSession.doWork { connection ->
        connection.createStatement().execute("SET transaction_mode = 'multi'")
      }
      for (i in 1..10) {
        session.save(DbMovie("Movie $i"))
      }
    }
  }
}
