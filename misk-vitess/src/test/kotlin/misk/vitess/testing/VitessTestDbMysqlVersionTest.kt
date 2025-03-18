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
class VitessTestDbMysqlVersionTest {

  @Test
  fun `test MySql version has correct defaults`() {
    val vitessTestDb = VitessTestDb(autoApplySchemaChanges = false)

    assertDoesNotThrow(vitessTestDb::run)

    val resultSet: ResultSet = executeQuery("SELECT @@global.version;", DefaultSettings.PORT)

    if (resultSet.next()) {
      val actualMysqlVersion = resultSet.getString(1)
      assertEquals("${DefaultSettings.MYSQL_VERSION}-Vitess", actualMysqlVersion)
    } else {
      Assertions.fail("Failed to get MySQL version.")
    }
  }

  @Test
  fun `test user defined MySql version`() {
    val vitessTestDb =
      VitessTestDb(
        autoApplySchemaChanges = false,
        containerName = "mysql_version_vitess_db",
        port = 61003,
        mysqlVersion = "8.0.42",
        keepAlive = false,
      )

    assertDoesNotThrow(vitessTestDb::run)

    val resultSet: ResultSet = executeQuery("SELECT @@global.version;", 61003)

    if (resultSet.next()) {
      val actualMysqlVersion = resultSet.getString(1)
      assertEquals("8.0.42-Vitess", actualMysqlVersion)
    } else {
      Assertions.fail("Failed to get MySQL version.")
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
