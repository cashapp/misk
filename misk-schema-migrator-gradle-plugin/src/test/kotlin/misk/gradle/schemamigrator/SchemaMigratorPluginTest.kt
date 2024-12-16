package misk.gradle.schemamigrator

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.Properties

class SchemaMigratorPluginTest {
  @ParameterizedTest
  @ValueSource(strings = [
    "/schema-migrator-plugin-test",
    "/schema-migrator-plugin-test-with-declarative-migrations-format"
  ])
  fun `schema migrator plugin migrates schemas`(projectDir: String) {
    val testProjectDir = File(this.javaClass.getResource(projectDir)!!.file)
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
      .forwardOutput()
      .build()

    assertThat(result.task(":migrateSchema")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

    datasource.connection.use { connection ->
      connection.createStatement().use { statement ->
        statement.execute("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = '${config.schema}' AND TABLE_NAME = 'people' AND COLUMN_NAME = 'nickname'")
        statement.resultSet.use { resultSet ->
          resultSet.next()
          assertThat(resultSet.getInt(1)).isEqualTo(1)
        }
      }
    }
  }
}
