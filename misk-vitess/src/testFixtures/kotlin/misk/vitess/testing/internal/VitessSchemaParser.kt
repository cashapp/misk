package misk.vitess.testing.internal

import misk.vitess.testing.VitessTestDbSchemaParseException
import java.io.File
import java.nio.file.Path

internal class VitessSchemaParser(
  private val lintSchema: Boolean,
  private val schemaDir: String,
  private val schemaDirPath: Path,
) {
  private val vschemaAdapter = VschemaAdapter()
  private val vschemaLinter = VschemaLinter(vschemaAdapter = vschemaAdapter)

  /**
   * Validate the user supplied schema directory contains a proper structure and return a list of [VitessKeyspace].
   *
   * The expected format for a schema directory is:
   * ```
   * /schema
   * ├── keyspace1
   * │ ├── v0001__add_table.sql
   * │ ├──vschema.json
   * ├── keyspace2
   * │ ├── v0002__add_table2.sql
   * │ ├── vschema.json
   * ```
   *
   * @return a list of [VitessKeyspace] parsed from the schema directory.
   * @throws [VitessTestDbStartupException] if the schema directory has an improper structure.
   */
  fun validateAndParse(): List<VitessKeyspace> {
    val schemaDirectory = File("$schemaDirPath")
    validateSchemaDirectoryStructure(schemaDirectory)
    return parseAndValidateVitessKeyspaces()
  }

  /** Validate that the user provided schema directory has a correct file structure. */
  private fun validateSchemaDirectoryStructure(schemaDirectory: File) {
    if (schemaDirectory.listFiles().isNullOrEmpty()) {
      throw VitessTestDbSchemaParseException(
        "Schema directory `$schemaDir` must contain keyspace directories with at least one .sql schema change file" +
          " and a vschema.json file."
      )
    }

    val exceptions = mutableListOf<String>()

    schemaDirectory.listFiles()?.forEach { schemaDirectoryFile ->
      // Skip files that start with a dot to support custom configuration
      if (schemaDirectoryFile.name.startsWith(".")) {
        return@forEach
      }

      if (!schemaDirectoryFile.isDirectory) {
        exceptions.add("`$schemaDirectoryFile` must be a keyspace directory.")
        return@forEach
      }

      if (schemaDirectoryFile.listFiles().isNullOrEmpty()) {
        exceptions.add(
          "Keyspace directory `${schemaDirectoryFile.name}` must contain at least one .sql schema change file" +
            " and a vschema.json file."
        )
        return@forEach
      }

      var vschemaFileExists = false
      var sqlSchemaChangeFileExists = false
      schemaDirectoryFile.listFiles()?.forEach { subFile ->
        if (!subFile.isFile) {
          exceptions.add(
            "Keyspace directory `${schemaDirectoryFile.name}` must only contain files. Found `${subFile.name}` which is not a file."
          )
        }

        if (subFile.name.endsWith(".sql")) {
          sqlSchemaChangeFileExists = true
        }

        if (subFile.name == "vschema.json") {
          vschemaFileExists = true
        }
      }

      if (!sqlSchemaChangeFileExists) {
        exceptions.add(
          "Keyspace directory `${schemaDirectoryFile.name}` must contain at least one .sql schema change file."
        )
      }

      if (!vschemaFileExists) {
        exceptions.add("Keyspace directory `${schemaDirectoryFile.name}` must contain a vschema.json file.")
      }
    }

    if (exceptions.isNotEmpty()) {
      throw VitessTestDbSchemaParseException(exceptions.joinToString(separator = "\n"))
    }
  }

  /** Parse a valid schema directory structure into a list of [VitessKeyspace], and check for schema level errors. */
  private fun parseAndValidateVitessKeyspaces(): List<VitessKeyspace> {
    val keyspaceDirs = schemaDirPath.toFile().listFiles { file -> file.isDirectory } ?: emptyArray()
    val keyspaces = mutableListOf<VitessKeyspace>()

    for (keyspaceDir in keyspaceDirs) {
      val vschemaFile = File(keyspaceDir, "vschema.json")
      var vschemaJson: Map<String, Any>
      var vschemaText: String
      try {
        vschemaText = vschemaFile.readText()
        vschemaJson = vschemaAdapter.fromJson(vschemaText)
      } catch (e: Exception) {
        throw VitessTestDbSchemaParseException(
          "Keyspace directory `${keyspaceDir.name}` must have a vschema.json file with a valid JSON format."
        )
      }

      if (lintSchema) {
        vschemaLinter.lint(vschemaJson, keyspaceDir.name)
      }

      val vschemaTables = parseTables(vschemaJson)

      if (vschemaTables.isEmpty()) {
        throw VitessTestDbSchemaParseException(
          "Keyspace directory `${keyspaceDir.name}` must have a vschema.json file with at least one table."
        )
      }

      // TODO(aparajon) Add the following future validations to give quick feedback on schema errors:
      // Verify sequence tables have a "vitess_sequence" comment in .sql files
      // Verify sharded tables do not use autoincrement in .sql files and have a sequence table in vschema.json.

      val sharded = (vschemaJson["sharded"] as? Boolean) ?: false
      val shards = if (sharded) 2 else 1
      val schemaChanges = readAndSanitizeSqlFiles(keyspaceDir)

      validateTables(schemaChanges, vschemaTables, keyspaceDir)

      keyspaces.add(
        VitessKeyspace(keyspaceDir.name, vschemaTables, sharded, shards, schemaChanges, vschemaText)
      )
    }

    return keyspaces
  }

  private fun validateTables(
    schemaChanges: List<Pair<String, String>>,
    vschemaTables: List<VitessTable>,
    keyspaceDir: File,
  ) {
    val sqlTableNames = getCreatedSqlTableNames(schemaChanges)
    val vschemaTableNames = vschemaTables.map { it.tableName }.toSet()
    if (vschemaTableNames != sqlTableNames) {
      val missingInVschema = sqlTableNames - vschemaTableNames
      val extraInVschema = vschemaTableNames - sqlTableNames
      val errorMessage =
        StringBuilder(
          "Mismatch between vschema tables and created tables in .sql files for keyspace `${keyspaceDir.name}`."
        )

      if (missingInVschema.isNotEmpty()) {
        errorMessage.append("\n\tMissing in vschema: $missingInVschema")
      }

      if (extraInVschema.isNotEmpty()) {
        errorMessage.append("\n\tExtra in vschema: $extraInVschema")
      }

      throw VitessTestDbSchemaParseException("$errorMessage")
    }
  }

  private fun parseTables(vschemaJson: Map<String, Any>): List<VitessTable> {
    val tables = mutableListOf<VitessTable>()
    val tablesMap = vschemaAdapter.toMap(vschemaJson["tables"])
    tablesMap.forEach { (tableName, value) ->
      val tableMap = vschemaAdapter.toMap(value)
      val tableTypeString = tableMap["type"]?.toString()
      val tableType =
        when (tableTypeString) {
          "sequence" -> VitessTableType.SEQUENCE
          "reference" -> VitessTableType.REFERENCE
          else -> VitessTableType.STANDARD
        }
      tables.add(VitessTable(tableName, tableType))
    }

    return tables
  }

  /**
   * Return a list of sanitized SQL files from a keyspace directory, where the key is the file name and the value is the
   * .sql file content with comments removed.
   */
  private fun readAndSanitizeSqlFiles(keyspaceDir: File): List<Pair<String, String>> {
    val sqlFiles = keyspaceDir.listFiles { _, name -> name.endsWith(".sql") } ?: return emptyList()
    return sqlFiles.map { file ->
      file.name to
        file.readText().lines().filterNot { it.trim().startsWith("--") || it.trim().startsWith("#") }.joinToString("\n")
    }
  }

  /**
   * Given a list of schema changes, return a set of tables that are created and not dropped. It's possible for the same
   * table to be created and dropped multiple times, and this function takes that behavior into account.
   */
  private fun getCreatedSqlTableNames(schemaChanges: List<Pair<String, String>>): Set<String> {
    // Table status is a map of table name to whether it is created (true) or dropped (false).
    val tableStatus = mutableMapOf<String, Boolean>()

    // In schemaChanges, the left entry of the pair is the file name, and the right entry is the sql content.
    // We want to iterate over files in sorted order (e.g. v1 -> v2) to respect the order of schema changes
    // in the traditional workflow.
    schemaChanges
      .sortedBy { it.first }
      .forEach { (_, sqlContent) ->
        // One .sql file can contain multiple statements separated by a semicolon.
        val statements = sqlContent.split(";")
        statements.forEach { statement ->
          val trimmedStatement = statement.trim()
          if (trimmedStatement.startsWith("CREATE TABLE", ignoreCase = true)) {
            val tableName = extractTableName(trimmedStatement)
            tableStatus[tableName] = true
          } else if (trimmedStatement.startsWith("DROP TABLE", ignoreCase = true)) {
            val tableName = extractTableName(trimmedStatement)
            tableStatus[tableName] = false
          }
        }
      }

    return tableStatus.filter { it.value }.keys.toSet()
  }

  private fun extractTableName(sqlLine: String): String {
    // Regex pattern to match on {CREATE | DROP | ALTER} TABLE {IF EXISTS} `table_name`,
    // where table_name can contain letters, digits, and underscores:
    // (?i) - makes the REGEX case insensitive. Capitalization is ignored
    // \\bTABLE\\b - matches a word "table" with non-word characters on either side
    // \\s+ - matches any number of spaces between "TABLE" and the table_name
    // (IF\\s+EXISTS\\s+)? - optionally matches "IF EXISTS" with any number of spaces
    // `? - optionally matches a ` character
    // ([a-zA-Z0-9_]+) - matches any number of alphanumeric characters or underscores. This is the second match group
    // retrieved below.
    val regex = Regex("(?i)\\bTABLE\\b\\s+(IF\\s+EXISTS\\s+)?`?([a-zA-Z0-9_]+)`?")
    val matchResult = regex.find(sqlLine)
    return matchResult?.groups?.get(2)?.value
      ?: throw VitessTestDbSchemaParseException("Failed to extract table name from SQL line: $sqlLine")
  }
}
