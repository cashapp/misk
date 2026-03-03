package misk.hibernate

import misk.jdbc.DataSourceType
import misk.jdbc.TableScanException
import misk.jdbc.uniqueLong
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import javax.inject.Inject

/**
 * Verifies that we're constraining a few things that makes apps hard to scale out.
 */
@MiskTest(startService = true)
class MySqlScaleSafetyTest {
  @MiskTestModule
  val module = MoviesTestModule(DataSourceType.MYSQL)

  @Inject @Movies lateinit var transacter: Transacter

  @Test
  @Disabled("flaky test, see https://github.com/cashapp/misk/issues/1464")
  fun tableScansDetected() {
    transacter.transaction { session ->
      val cf = session.save(DbActor("Carrie Fisher", null))
      val sw = session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
      session.save(DbCharacter("Leia Organa", session.load(sw), session.load(cf)))

      assertThrows<TableScanException> {
        session.useConnection { connection ->
          connection.createStatement().use { statement ->
            connection.prepareStatement("SELECT COUNT(*) FROM characters WHERE name = ?")
              .use { s ->
                s.setString(1, "Leia Organa")
                s.executeQuery().uniqueLong()
              }
          }
        }
      }

    }
  }

  @Test
  fun tableScansCanBeDisabled() {
    transacter.transaction { session ->
      session.withoutChecks(Check.TABLE_SCAN) {
        session.useConnection { c ->
          c.prepareStatement("SELECT COUNT(*) FROM characters WHERE name = ?").use { s ->
            s.setString(1, "Leia Organa")
            s.executeQuery().uniqueLong()
          }
        }
      }
    }
  }
}
