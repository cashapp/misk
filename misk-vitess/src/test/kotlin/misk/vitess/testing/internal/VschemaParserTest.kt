package misk.vitess.testing.internal

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import misk.vitess.testing.VitessTestDbSchemaParseException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VitessSchemaParserTest {
  private val schemaName = "schema"

  @Test
  fun `test directory is empty`() {
    val emptyDirPath = Files.createTempDirectory("empty_dir")
    val parser = VitessSchemaParser(false, schemaName, emptyDirPath)

    val exception = assertThrows<VitessTestDbSchemaParseException> { parser.validateAndParse() }
    assertEquals(
      "Schema directory `$schemaName` must contain keyspace directories with at least one .sql schema change file and a vschema.json file.",
      exception.message,
    )
  }

  @Test
  fun `test keyspace directory is empty`() {
    val schemaDirPath = Files.createTempDirectory("schema_dir")
    val keyspaceDir = File(schemaDirPath.toFile(), "keyspace1")
    keyspaceDir.mkdir()
    val parser = VitessSchemaParser(false, schemaName, schemaDirPath)

    val exception = assertThrows<VitessTestDbSchemaParseException> { parser.validateAndParse() }
    assertEquals(
      "Keyspace directory `${keyspaceDir.name}` must contain at least one .sql schema change file and a vschema.json file.",
      exception.message,
    )
  }

  @Test
  fun `test keyspace directory contains non-file directory`() {
    val schemaDirPath = Files.createTempDirectory("schema_dir")
    val keyspaceDir = File(schemaDirPath.toFile(), "keyspace1")
    keyspaceDir.mkdir()
    val nonFileDir = File(keyspaceDir, "non_file_dir")
    nonFileDir.mkdir()
    val parser = VitessSchemaParser(false, schemaName, schemaDirPath)

    val exception = assertThrows<VitessTestDbSchemaParseException> { parser.validateAndParse() }
    assertTrue(exception.message!!.contains("Keyspace directory `${keyspaceDir.name}` must only contain files."))
    assertTrue(
      exception.message!!.contains(
        "Keyspace directory `${keyspaceDir.name}` must contain at least one .sql schema change file."
      )
    )
    assertTrue(
      exception.message!!.contains("Keyspace directory `${keyspaceDir.name}` must contain a vschema.json file.")
    )
  }

  @Test
  fun `test keyspace directory does not contain sql schema change files`() {
    val schemaDirPath = Files.createTempDirectory("schema_dir")
    val keyspaceDir = File(schemaDirPath.toFile(), "keyspace1")
    keyspaceDir.mkdir()
    val vschemaFile = File(keyspaceDir, "vschema.json")
    vschemaFile.createNewFile()

    val parser = VitessSchemaParser(false, schemaName, schemaDirPath)

    val exception = assertThrows<VitessTestDbSchemaParseException> { parser.validateAndParse() }
    assertEquals(
      "Keyspace directory `${keyspaceDir.name}` must contain at least one .sql schema change file.",
      exception.message,
    )
  }

  @Test
  fun `test keyspace directory does not contain vschema json file`() {
    val schemaDirPath = Files.createTempDirectory("schema_dir")
    val keyspaceDir = File(schemaDirPath.toFile(), "keyspace1")
    keyspaceDir.mkdir()
    val sqlFile = File(keyspaceDir, "v0001__add_table.sql")
    sqlFile.createNewFile()
    val parser = VitessSchemaParser(false, schemaName, schemaDirPath)

    val exception = assertThrows<VitessTestDbSchemaParseException> { parser.validateAndParse() }
    assertEquals("Keyspace directory `${keyspaceDir.name}` must contain a vschema.json file.", exception.message)
  }

  @Test
  fun `test schema directory has an invalid vschema file format`() {
    val schemaDirPath = Files.createTempDirectory("schema_dir")
    val keyspaceDir = File(schemaDirPath.toFile(), "keyspace1")
    keyspaceDir.mkdir()
    val sqlFile = File(keyspaceDir, "v0001__add_table.sql")
    sqlFile.createNewFile()
    val vschemaFile = File(keyspaceDir, "vschema.json")
    vschemaFile.createNewFile()
    val parser = VitessSchemaParser(false, schemaName, schemaDirPath)

    val exception = assertThrows<VitessTestDbSchemaParseException> { parser.validateAndParse() }
    assertEquals(
      "Keyspace directory `${keyspaceDir.name}` must have a vschema.json file with a valid JSON format.",
      exception.message,
    )
  }

  @Test
  fun `test schema directory has a vschema with no tables`() {
    val schemaDirPath = Files.createTempDirectory("schema_dir")
    val keyspaceDir = File(schemaDirPath.toFile(), "keyspace1")
    keyspaceDir.mkdir()
    val sqlFile = File(keyspaceDir, "v0001__add_table.sql")
    sqlFile.createNewFile()
    val vschemaFile = File(keyspaceDir, "vschema.json")
    vschemaFile.createNewFile()
    vschemaFile.writeText("{\"tables\": {}}")
    val parser = VitessSchemaParser(false, schemaName, schemaDirPath)

    val exception = assertThrows<VitessTestDbSchemaParseException> { parser.validateAndParse() }
    assertEquals(
      "Keyspace directory `${keyspaceDir.name}` must have a vschema.json file with at least one table.",
      exception.message,
    )
  }

  @Test
  fun `test valid schema directory`() {
    val classLoader = this::class.java.classLoader
    val schemaDirPath = Paths.get(classLoader.getResource("vitess/schema")!!.toURI())
    val parser = VitessSchemaParser(false, "vitess/schema", schemaDirPath)

    val keyspaces = parser.validateAndParse().sortedBy { it.name }
    assertArrayEquals(arrayOf("gameworld", "gameworld_sharded"), keyspaces.map { it.name }.toTypedArray())

    val unshardedKeyspace = keyspaces[0]
    assertEquals("gameworld", unshardedKeyspace.name)
    assertEquals(1, unshardedKeyspace.shards)
    assertEquals(false, unshardedKeyspace.sharded)
    assertEquals(false, unshardedKeyspace.sharded)
    val unshardedTables = unshardedKeyspace.tables.sortedBy { it.tableName }
    assertArrayEquals(arrayOf("customers_seq", "games_seq"), unshardedTables.map { it.tableName }.toTypedArray())
    val unshardedDdls = unshardedKeyspace.ddlCommands.joinToString("\n")
    assertTrue(unshardedDdls.contains("CREATE TABLE `customers_seq`"))
    assertTrue(unshardedDdls.contains("CREATE TABLE `games_seq`"))
    assertTrue(unshardedKeyspace.vschema.contains("customers_seq"))
    assertTrue(unshardedKeyspace.vschema.contains("games_seq"))

    val shardedKeyspace = keyspaces[1]
    assertEquals("gameworld_sharded", shardedKeyspace.name)
    assertEquals(2, shardedKeyspace.shards)
    assertEquals(true, shardedKeyspace.sharded)
    val shardedTables = shardedKeyspace.tables.sortedBy { it.tableName }
    assertArrayEquals(arrayOf("customers", "games"), shardedTables.map { it.tableName }.toTypedArray())
    val shardedDdls = shardedKeyspace.ddlCommands.joinToString("\n")
    assertTrue(shardedDdls.contains("CREATE TABLE `customers`"))
    assertTrue(shardedDdls.contains("CREATE TABLE `games`"))
    assertTrue(unshardedKeyspace.vschema.contains("customers"))
    assertTrue(unshardedKeyspace.vschema.contains("games"))
  }

  @Test
  fun `test multiple keyspace directories with one invalid directory`() {
    val schemaDirPath = Files.createTempDirectory("schema_dir")

    // Create valid keyspace directory
    val validKeyspaceDir = File(schemaDirPath.toFile(), "validKeyspace")
    validKeyspaceDir.mkdir()
    val sqlFile = File(validKeyspaceDir, "v0001__add_table.sql")
    sqlFile.createNewFile()
    val vschemaFile = File(validKeyspaceDir, "vschema.json")
    vschemaFile.createNewFile()

    // Create invalid keyspace directory
    val invalidKeyspaceDir = File(schemaDirPath.toFile(), "invalidKeyspace")
    invalidKeyspaceDir.mkdir()
    val invalidFile = File(invalidKeyspaceDir, "invalid.txt")
    invalidFile.createNewFile()

    val parser = VitessSchemaParser(false, schemaName, schemaDirPath)

    val exception = assertThrows<VitessTestDbSchemaParseException> { parser.validateAndParse() }

    assertTrue(
      exception.message!!.contains(
        "Keyspace directory `${invalidKeyspaceDir.name}` must contain at least one .sql schema change file."
      )
    )
    assertTrue(
      exception.message!!.contains("Keyspace directory `${invalidKeyspaceDir.name}` must contain a vschema.json file.")
    )
  }

  @Test
  fun `test table validation with matching tables`() {
    val schemaDirPath = Files.createTempDirectory("schema_dir")
    val keyspaceDir = File(schemaDirPath.toFile(), "keyspace1")
    keyspaceDir.mkdir()
    val sqlFile = File(keyspaceDir, "v0001__add_table.sql")
    sqlFile.writeText("CREATE TABLE `test_table` (id int primary key);")
    val vschemaFile = File(keyspaceDir, "vschema.json")
    vschemaFile.writeText("{\"tables\": {\"test_table\": {}}}")

    val parser = VitessSchemaParser(false, schemaName, schemaDirPath)
    val keyspaces = parser.validateAndParse()
    assertTrue(keyspaces.isNotEmpty())
  }

  @Test
  fun `test table validation with missing table in vschema`() {
    val schemaDirPath = Files.createTempDirectory("schema_dir")
    val keyspaceDir = File(schemaDirPath.toFile(), "keyspace1")
    keyspaceDir.mkdir()
    val sqlFile = File(keyspaceDir, "v0001__add_table.sql")
    sqlFile.writeText("CREATE TABLE `test_table` (id int primary key);")
    val sqlFile2 = File(keyspaceDir, "v0002__add_table2.sql")
    sqlFile2.writeText("CREATE TABLE `test_table2` (id int primary key);")
    val vschemaFile = File(keyspaceDir, "vschema.json")
    vschemaFile.writeText("{\"tables\": {\"test_table2\": {}}}")

    val parser = VitessSchemaParser(false, schemaName, schemaDirPath)
    val exception = assertThrows<VitessTestDbSchemaParseException> { parser.validateAndParse() }
    assertTrue(exception.message!!.contains("Missing in vschema: [test_table]"))
  }

  @Test
  fun `test table validation with extra table in vschema`() {
    val schemaDirPath = Files.createTempDirectory("schema_dir")
    val keyspaceDir = File(schemaDirPath.toFile(), "keyspace1")
    keyspaceDir.mkdir()
    val sqlFile = File(keyspaceDir, "v0001__add_table.sql")
    sqlFile.writeText("CREATE TABLE `test_table` (id int primary key);")
    val vschemaFile = File(keyspaceDir, "vschema.json")
    vschemaFile.writeText("{\"tables\": {\"test_table\": {}, \"extra_table\": {}}}")

    val parser = VitessSchemaParser(false, schemaName, schemaDirPath)
    val exception = assertThrows<VitessTestDbSchemaParseException> { parser.validateAndParse() }
    assertTrue(exception.message!!.contains("Extra in vschema: [extra_table]"))
  }

  @Test
  fun `test table validation with missing and extra tables`() {
    val schemaDirPath = Files.createTempDirectory("schema_dir")
    val keyspaceDir = File(schemaDirPath.toFile(), "keyspace1")
    keyspaceDir.mkdir()
    val sqlFile = File(keyspaceDir, "v0001__add_table.sql")
    sqlFile.writeText("CREATE TABLE `test_table` (id int primary key);")
    val vschemaFile = File(keyspaceDir, "vschema.json")
    vschemaFile.writeText("{\"tables\": {\"extra_table\": {}}}")

    val parser = VitessSchemaParser(false, schemaName, schemaDirPath)
    val exception = assertThrows<VitessTestDbSchemaParseException> { parser.validateAndParse() }
    assertEquals(
      """Mismatch between vschema tables and created tables in .sql files for keyspace `keyspace1`.
	Missing in vschema: [test_table]
	Extra in vschema: [extra_table]""",
      exception.message,
    )
  }

  @Test
  fun `test table validation with multi-statement sql file`() {
    val schemaDirPath = Files.createTempDirectory("schema_dir")
    val keyspaceDir = File(schemaDirPath.toFile(), "keyspace1")
    keyspaceDir.mkdir()
    val sqlFile = File(keyspaceDir, "v0001__add_tables.sql")
    sqlFile.writeText(
      """
      CREATE TABLE `test_table1` (id int primary key);
      CREATE TABLE `test_table2` (id int primary key);
      DROP TABLE `test_table1`;
      """
        .trimIndent()
    )
    val vschemaFile = File(keyspaceDir, "vschema.json")
    vschemaFile.writeText("{\"tables\": {\"test_table2\": {}}}")

    val parser = VitessSchemaParser(false, schemaName, schemaDirPath)
    val keyspaces = parser.validateAndParse()
    assertTrue(keyspaces.isNotEmpty())
    val tables = keyspaces[0].tables.map { it.tableName }.toSet()
    assertEquals(setOf("test_table2"), tables)
  }

  @Test
  fun `test table validation with DROP TABLE IF EXISTS`() {
    val schemaDirPath = Files.createTempDirectory("schema_dir")
    val keyspaceDir = File(schemaDirPath.toFile(), "keyspace1")
    keyspaceDir.mkdir()
    val sqlFile = File(keyspaceDir, "v0001__add_and_drop_table.sql")
    sqlFile.writeText(
      """
      CREATE TABLE `test_table` (id int primary key);
      CREATE TABLE `test_table2` (id int primary key);
      DROP TABLE IF EXISTS `test_table`;
      """
        .trimIndent()
    )
    val vschemaFile = File(keyspaceDir, "vschema.json")
    vschemaFile.writeText("{\"tables\": {\"test_table2\": {}}}")

    val parser = VitessSchemaParser(false, schemaName, schemaDirPath)
    val keyspaces = parser.validateAndParse()
    assertTrue(keyspaces.isNotEmpty())
    val tables = keyspaces[0].tables.map { it.tableName }.toSet()
    assertEquals(setOf("test_table2"), tables)
  }
}
