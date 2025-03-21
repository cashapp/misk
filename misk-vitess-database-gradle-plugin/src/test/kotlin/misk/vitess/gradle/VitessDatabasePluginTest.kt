package misk.vitess.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement

class VitessDatabasePluginTest {
  @Test
  fun `start VitessTestDb with custom parameters`() {
    val testProjectDir = File(this.javaClass.getResource("/vitess-database-plugin-test")!!.file)

    val result = GradleRunner.create()
      .withProjectDir(testProjectDir)
      .withArguments("startVitessDatabase")
      .withPluginClasspath()
      .forwardOutput()
      .build()

    assertThat(result.task(":startVitessDatabase")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

    val keyspaceResults: ResultSet = executeQuery("SHOW KEYSPACES;")
    var rowCount = 0
    while (keyspaceResults.next()) {
      rowCount++
    }

    assertEquals(2, rowCount)

    val txnIsoLevelResults = executeQuery("SELECT @@global.transaction_ISOLATION;")
    txnIsoLevelResults.next()
    val actualTransactionIsolationLevel = txnIsoLevelResults.getString(1)
    assertEquals("READ-COMMITTED", actualTransactionIsolationLevel)
  }

  private fun executeQuery(query: String): ResultSet {
    val url = "jdbc:mysql://localhost:31503/@primary"
    val user = "root"
    val password = ""
    val connection: Connection = DriverManager.getConnection(url, user, password)
    val statement: Statement = connection.createStatement()
    return statement.executeQuery(query)
  }
}
