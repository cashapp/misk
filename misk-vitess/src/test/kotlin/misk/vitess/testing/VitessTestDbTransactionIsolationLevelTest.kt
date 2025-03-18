package misk.vitess.testing

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class VitessTestDbTransactionIsolationLevelTest {

  @Test
  fun `test transaction isolation level has correct defaults`() {
    val vitessTestDb = VitessTestDb(autoApplySchemaChanges = false)

    assertDoesNotThrow(vitessTestDb::run)

    val resultSet: ResultSet = executeQuery("SELECT @@global.transaction_ISOLATION;", 27003)

    if (resultSet.next()) {
      val actualTransactionIsolationLevel = resultSet.getString(1)
      assertEquals(TransactionIsolationLevel.REPEATABLE_READ.value, actualTransactionIsolationLevel)
    } else {
      Assertions.fail("Failed to get transaction isolation level.")
    }
  }

  @Test
  fun `test user defined transaction isolation level`() {
    val vitessTestDb =
      VitessTestDb(
        autoApplySchemaChanges = false,
        containerName = "transaction_isolation_level_vitess_db",
        port = 31003,
        transactionIsolationLevel = TransactionIsolationLevel.READ_COMMITTED,
      )

    assertDoesNotThrow(vitessTestDb::run)

    val resultSet: ResultSet = executeQuery("SELECT @@global.transaction_ISOLATION;", 31003)

    if (resultSet.next()) {
      val actualTransactionIsolationLevel = resultSet.getString(1)
      assertEquals(TransactionIsolationLevel.READ_COMMITTED.value, actualTransactionIsolationLevel)
    } else {
      Assertions.fail("Failed to get transaction isolation level.")
    }
  }

  private fun executeQuery(query: String, port: Int): ResultSet {
    val user = "root"
    val password = ""
    val url = "jdbc:mysql://localhost:$port/@primary"

    val connection: Connection = DriverManager.getConnection(url, user, password)
    val statement: Statement = connection.createStatement()
    return statement.executeQuery(query)
  }
}
