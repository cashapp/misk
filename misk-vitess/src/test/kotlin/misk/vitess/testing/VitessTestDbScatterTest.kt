package misk.vitess.testing

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VitessTestDbScatterTest {

  private var url = "jdbc:mysql://localhost:27003/@primary"
  private var user = "root"
  private var password = ""

  @Test
  fun `test scatter queries fail`() {
    val vitessTestDb = createNoScatterDb()
    assertDoesNotThrow(vitessTestDb::run)

    val scatterQuery = "SELECT * FROM customers;"
    val exception = assertThrows<SQLException> { executeQuery(scatterQuery) }

    assertTrue(exception.message!!.contains("plan includes scatter, which is disallowed"))
  }

  @Test
  fun `test disabling scatters fails on an unsupported version`() {
    val exception = assertThrows<RuntimeException> { createUnsupportedNoScatterDb().run() }
    Assertions.assertEquals(
      "Vitess image version must be >= 20 when scatters are disabled, found 19.",
      exception.message,
    )
  }

  private fun createNoScatterDb(): VitessTestDb {
    return VitessTestDb(enableScatters = false, vitessImage = "vitess/vttestserver:v20.0.5-mysql80", vitessVersion = 20)
  }

  private fun createUnsupportedNoScatterDb(): VitessTestDb {
    return VitessTestDb(enableScatters = false, vitessImage = "vitess/vttestserver:v19.0.9-mysql80")
  }

  private fun executeQuery(query: String): ResultSet {
    val connection: Connection = DriverManager.getConnection(url, user, password)
    val statement: Statement = connection.createStatement()
    return statement.executeQuery(query)
  }
}
