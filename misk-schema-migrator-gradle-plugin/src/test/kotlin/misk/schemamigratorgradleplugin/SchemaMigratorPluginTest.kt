package misk.schemamigratorgradleplugin

import org.gradle.internal.impldep.com.zaxxer.hikari.HikariConfig
import org.gradle.internal.impldep.com.zaxxer.hikari.HikariDataSource
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import org.junit.jupiter.api.Test
import java.util.Properties

class SchemaMigratorPluginTest {
  @Test
  fun `schema migrator plugin migrates schems`() {
    val testProjectDir = File(this.javaClass.getResource("/schema-migrator-plugin-test")!!.file)
    val properties = Properties()
    properties.load(File(testProjectDir, "src/main/resources/db.properties").inputStream())

    val config = HikariConfig()
    config.jdbcUrl = properties.getProperty("jdbcUrl")
    config.username = properties.getProperty("username")
    config.password = properties.getProperty("password")
    config.schema = properties.getProperty("schema")
    val datasource = HikariDataSource(config)

    datasource.connection.use { connection ->
      connection.createStatement().use { statement ->
        statement.execute("DROP DATABASE IF EXISTS ${config.schema};")
      }
    }

    val result = GradleRunner.create()
      .withProjectDir(testProjectDir)
      .withArguments("migrateSchema")
      .withPluginClasspath()
      .build()

    assertTrue(result.task(":migrateSchema")!!.outcome == TaskOutcome.SUCCESS)

    datasource.connection.use { connection ->
      connection.createStatement().use { statement ->
        statement.execute("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = '${config.schema}' AND TABLE_NAME = 'people' AND COLUMN_NAME = 'nickname'")
        statement.resultSet.use { resultSet ->
          resultSet.next()
          assertTrue(resultSet.getInt(1) == 1)
        }
      }
    }
  }
}
