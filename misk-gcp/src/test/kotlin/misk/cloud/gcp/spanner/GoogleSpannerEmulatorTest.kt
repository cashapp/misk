package misk.cloud.gcp.spanner

import com.google.cloud.spanner.DatabaseId
import com.google.cloud.spanner.Key
import com.google.cloud.spanner.KeySet
import com.google.cloud.spanner.Mutation
import com.google.cloud.spanner.Spanner
import com.google.cloud.spanner.SpannerOptions
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import wisp.containers.ContainerUtil
import jakarta.inject.Inject
import org.junit.jupiter.api.RepeatedTest

@MiskTest(startService = true)
class GoogleSpannerEmulatorTest {
  val spannerConfig = SpannerConfig(
    project_id = "test-project",
    instance_id = "test-instance",
    database = "test-database",
    emulator = SpannerEmulatorConfig(
      enabled = true,
      hostname = ContainerUtil.dockerTargetOrLocalHost(),
      version = "1.4.9",
    )
  )

  @MiskTestModule
  val module = GoogleSpannerTestModule(spannerConfig)

  @Inject lateinit var emulator: GoogleSpannerEmulator

  val spannerClient: Spanner = SpannerOptions.newBuilder()
    .setProjectId(spannerConfig.project_id)
    .setEmulatorHost(
      "${spannerConfig.emulator.hostname}:${spannerConfig.emulator.port}"
    )
    .build()
    .service

  @Nested
  inner class `#reset` {
    @RepeatedTest(3)
    fun `clears tables on each test when using reusable test module`() {
      // The emulator will create the database and instance for us using the
      // config, so we don't need to worry about checking for their existence.
      val adminClient = spannerClient.instanceAdminClient
        .getInstance(spannerConfig.instance_id)
        .getDatabase(spannerConfig.database)
      val dataClient = spannerClient.getDatabaseClient(
        DatabaseId.of(
          spannerConfig.project_id,
          spannerConfig.instance_id,
          spannerConfig.database,
        )
      )
      val personId = "abc123"
      fun personExists(): Boolean {
        val query = dataClient.singleUseReadOnlyTransaction().read(
          "people",
          KeySet.singleKey(
            Key.of(personId)
          ),
          listOf("id")
        )

        // Load results
        if (query.next()) {
          return personId == query.getString(0)
        } else {
          return false
        }
      }

      // Check if there's a people table.
      val hasPeopleTable = adminClient.ddl.any { it.contains("CREATE TABLE people") }

      // If it doesn't exist, create it
      if (!hasPeopleTable) {
        // Create a "people" table
        adminClient.updateDdl(
          listOf(
            """
            CREATE TABLE people (id STRING(6)) PRIMARY KEY (id)
            """.trimIndent()
          ),
          null
        ).get()
      }

      // Expect that no person exists when test is started
      assertFalse(personExists())

      // Insert a person
      dataClient.write(
        listOf(
          Mutation.newInsertBuilder("people")
            .set("id").to(personId)
            .build()
        )
      )

      // Expect that the person exists in the DB
      assertTrue(personExists())
    }
  }

  @Nested
  inner class `#clearTables` {
    @Test fun `truncates all tables`() {
      // The emulator will create the database and instance for us using the
      // config, so we don't need to worry about checking for their existence.
      val adminClient = spannerClient.instanceAdminClient
        .getInstance(spannerConfig.instance_id)
        .getDatabase(spannerConfig.database)
      val dataClient = spannerClient.getDatabaseClient(
        DatabaseId.of(
          spannerConfig.project_id,
          spannerConfig.instance_id,
          spannerConfig.database,
        )
      )
      val personId = "abc123"
      fun personExists(): Boolean {
        val query = dataClient.singleUseReadOnlyTransaction().read(
          "people",
          KeySet.singleKey(
            Key.of(personId)
          ),
          listOf("id")
        )

        // Load results
        if (query.next()) {
          return personId == query.getString(0)
        } else {
          return false
        }
      }

      // Check if there's a people table.
      val hasPeopleTable = adminClient.ddl.any { it.contains("CREATE TABLE people") }

      // If it doesn't exist, create it
      if (!hasPeopleTable) {
        // Create a "people" table
        adminClient.updateDdl(
          listOf(
            """
            CREATE TABLE people (id STRING(6)) PRIMARY KEY (id)
            """.trimIndent()
          ),
          null
        ).get()
      }

      // Insert a person
      dataClient.write(
        listOf(
          Mutation.newInsertBuilder("people")
            .set("id").to(personId)
            .build()
        )
      )

      // Expect that the person exists in the DB
      assertTrue(personExists())

      // Clear the tables
      emulator.clearTables()

      // Expect that no person exists
      assertFalse(personExists())
    }
  }
}
