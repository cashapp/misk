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
class VitessTestDbSQLModeTest {

  @Test
  fun `test SQL mode has correct defaults`() {
    val vitessTestDb = VitessTestDb(autoApplySchemaChanges = false)
    assertDoesNotThrow(vitessTestDb::run)

    val resultSet: ResultSet = executeQuery("SELECT @@GLOBAL.sql_mode;", 27003)

    if (resultSet.next()) {
      val sqlMode = resultSet.getString(1)
      assertEquals(
        "ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION",
        sqlMode,
      )
    } else {
      Assertions.fail("Failed to get SQL mode.")
    }
  }

  @Test
  fun `test user defined SQL mode`() {
    //
    val userDefinedSqlMode = "ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION"

    val vitessTestDb =
      VitessTestDb(
        autoApplySchemaChanges = false,
        containerName = "vitess_test_db_custom_sql_mode",
        port = 31003,
        sqlMode = userDefinedSqlMode,
      )

    assertDoesNotThrow(vitessTestDb::run)

    val resultSet: ResultSet = executeQuery("SELECT @@GLOBAL.sql_mode;", 31003)

    if (resultSet.next()) {
      val actualSqlMode = resultSet.getString(1)
      assertEquals(userDefinedSqlMode, actualSqlMode)
    } else {
      Assertions.fail("Failed to get SQL mode.")
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
