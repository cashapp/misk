package misk.vitess.testing.internal

import misk.vitess.testing.VitessTestDb
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.pathString
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VitessSchemaManagerTest {
  @Test
  fun `test auto apply`() {
    val port = 27903
    val testDb =
      VitessTestDb(
        containerName = "schema_manager_auto_apply_vitess_db",
        autoApplySchemaChanges = true,
        keepAlive = false,
        port = port,
        vitessImage = "vitess/vttestserver:v20.0.6-mysql80",
        vitessVersion = 20,
      )
    testDb.run()

    val vitessQueryExecutor = VitessQueryExecutor(VitessClusterConfig(port))
    val keyspaces = vitessQueryExecutor.getKeyspaces()
    assertArrayEquals(arrayOf("gameworld", "gameworld_sharded"), keyspaces.toTypedArray())

    val unshardedTables = vitessQueryExecutor.getTables("gameworld").map { it.tableName }
    assertArrayEquals(arrayOf("customers_seq", "games_seq"), unshardedTables.toTypedArray())

    val shardedTables = vitessQueryExecutor.getTables("gameworld_sharded").map { it.tableName }
    assertArrayEquals(arrayOf("customers", "games"), shardedTables.toTypedArray())
  }

  @Test
  fun `test auto apply with declarative schema changes`() {
    val port = 27903
    val testDb =
      VitessTestDb(
        containerName = "schema_manager_auto_apply_declarative_vitess_db",
        autoApplySchemaChanges = true,
        enableDeclarativeSchemaChanges = true,
        keepAlive = false,
        port = port,
        vitessImage = "vitess/vttestserver:v20.0.6-mysql80",
        vitessVersion = 20,
      )
    testDb.run()

    val vitessQueryExecutor = VitessQueryExecutor(VitessClusterConfig(port))
    val keyspaces = vitessQueryExecutor.getKeyspaces()
    assertArrayEquals(arrayOf("gameworld", "gameworld_sharded"), keyspaces.toTypedArray())

    val unshardedTables = vitessQueryExecutor.getTables("gameworld").map { it.tableName }
    assertArrayEquals(arrayOf("customers_seq", "games_seq"), unshardedTables.toTypedArray())

    val shardedTables = vitessQueryExecutor.getTables("gameworld_sharded").map { it.tableName }
    assertArrayEquals(arrayOf("customers", "games"), shardedTables.toTypedArray())
  }

  @Test
  fun `test apply after run`() {
    val port = 27913
    val testDb =
      VitessTestDb(
        containerName = "schema_manager_manual_apply_vitess_db",
        autoApplySchemaChanges = false,
        keepAlive = false,
        port = port,
        vitessImage = "vitess/vttestserver:v20.0.6-mysql80",
        vitessVersion = 20,
      )
    val runResult = testDb.run()

    val vitessQueryExecutor = VitessQueryExecutor(VitessClusterConfig(port))
    // Keyspaces are still applied even if autoApplySchemaChanges is false.
    val keyspaces = vitessQueryExecutor.getKeyspaces()
    assertArrayEquals(arrayOf("gameworld", "gameworld_sharded"), keyspaces.toTypedArray())

    // However tables should not yet be applied.
    assertEquals(vitessQueryExecutor.getTables("gameworld").size, 0)
    assertEquals(vitessQueryExecutor.getTables("gameworld_sharded").size, 0)

    // Truncating should work without needing to create a temporary schema.
    testDb.truncate()
    val schemaDirPath = Paths.get("/tmp/vitess-test-db/${runResult.containerId}/schema")
    assertFalse(Files.exists(schemaDirPath), "Schema directory ${schemaDirPath.pathString} should not exist")

    var applySchemaResult = testDb.applySchema()
    // Now /tmp/{container_id}/schema should exist.
    assertTrue(Files.exists(schemaDirPath), "Schema directory ${schemaDirPath.pathString} should exist")

    // The vschema is always applied for each keyspace.
    assertEquals(2, applySchemaResult.vschemaUpdates.size)
    // In traditional schema changes, the DDL's are processed as is per .sql file.
    assertEquals(4, applySchemaResult.ddlUpdates.size)

    val unshardedTables = vitessQueryExecutor.getTables("gameworld").map { it.tableName }
    assertArrayEquals(arrayOf("customers_seq", "games_seq"), unshardedTables.toTypedArray())

    val shardedTables = vitessQueryExecutor.getTables("gameworld_sharded").map { it.tableName }
    assertArrayEquals(arrayOf("customers", "games"), shardedTables.toTypedArray())
  }

  @Test
  fun `test apply after run with declarative schema changes`() {
    val port = 27913
    val testDb =
      VitessTestDb(
        containerName = "schema_manager_manual_apply_vitess_db",
        autoApplySchemaChanges = false,
        enableDeclarativeSchemaChanges = true,
        keepAlive = false,
        port = port,
        vitessImage = "vitess/vttestserver:v20.0.6-mysql80",
        vitessVersion = 20,
      )
    val runResult = testDb.run()

    val vitessQueryExecutor = VitessQueryExecutor(VitessClusterConfig(port))
    // Keyspaces are still applied even if autoApplySchemaChanges is false.
    val keyspaces = vitessQueryExecutor.getKeyspaces()
    assertArrayEquals(arrayOf("gameworld", "gameworld_sharded"), keyspaces.toTypedArray())

    // However tables should not yet be applied.
    assertEquals(vitessQueryExecutor.getTables("gameworld").size, 0)
    assertEquals(vitessQueryExecutor.getTables("gameworld_sharded").size, 0)

    // Truncating should work without needing to create a temporary schema.
    testDb.truncate()
    val schemaDirPath = Paths.get("/tmp/vitess-test-db/${runResult.containerId}/schema")
    assertFalse(Files.exists(schemaDirPath), "Schema directory ${schemaDirPath.pathString} should not exist")

    var applySchemaResult = testDb.applySchema()
    // Now /tmp/{container_id}/schema should exist.
    assertTrue(Files.exists(schemaDirPath), "Schema directory ${schemaDirPath.pathString} should exist")

    // The vschema is always applied for each keyspace.
    assertEquals(2, applySchemaResult.vschemaUpdates.size)
    // In declarative schema changes, the DDL's get consolidated as one diff per keyspace.
    assertEquals(2, applySchemaResult.ddlUpdates.size)

    val unshardedTables = vitessQueryExecutor.getTables("gameworld").map { it.tableName }
    assertArrayEquals(arrayOf("customers_seq", "games_seq"), unshardedTables.toTypedArray())

    val shardedTables = vitessQueryExecutor.getTables("gameworld_sharded").map { it.tableName }
    assertArrayEquals(arrayOf("customers", "games"), shardedTables.toTypedArray())
  }
}
