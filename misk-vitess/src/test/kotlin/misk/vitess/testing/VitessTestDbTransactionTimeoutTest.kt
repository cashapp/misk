package misk.vitess.testing

import com.mysql.cj.jdbc.exceptions.MySQLQueryInterruptedException
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class VitessTestDbTransactionTimeoutTest {
  private companion object {
    const val PORT = 51003
    const val URL = "jdbc:mysql://localhost:$PORT/@primary"
    const val USER = "root"
    const val PASSWORD = ""
  }

  @Test
  fun `test transaction timeout`() {
    val vitessTestDb =
      VitessTestDb(
        autoApplySchemaChanges = false,
        containerName = "transaction_timeout_vitess_db",
        debugStartup = true,
        port = PORT,
        keepAlive = false,
        transactionTimeoutSeconds = Duration.ofSeconds(5),
      )

    assertDoesNotThrow(vitessTestDb::run)
    val exception = assertThrows<MySQLQueryInterruptedException> { executeTransaction("SELECT SLEEP(10);") }
    val actualMessage = exception.message!!
    val expectedMessageSubstring = "context deadline exceeded"
    assertTrue(
      actualMessage.contains(expectedMessageSubstring),
      "Expected message to contain \"$expectedMessageSubstring\" but was \"$actualMessage\"",
    )
  }

  private fun executeTransaction(query: String): ResultSet {
    DriverManager.getConnection(URL, USER, PASSWORD).use { connection ->
      connection.autoCommit = false
      connection.createStatement().use { statement ->
        val resultSet: ResultSet = statement.executeQuery(query)
        connection.commit()
        return resultSet
      }
    }
  }
}
