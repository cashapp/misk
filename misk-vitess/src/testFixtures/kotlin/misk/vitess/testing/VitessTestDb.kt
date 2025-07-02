package misk.vitess.testing

import misk.vitess.testing.internal.VitessDockerContainer
import misk.vitess.testing.internal.VitessQueryExecutor
import java.sql.Connection
import java.time.Duration
import kotlin.time.measureTime

/**
 * `VitessTestDb` is a class used to start a local Vitess database for tests.
 *
 * @property autoApplySchemaChanges Whether to automatically apply schema changes. Default is `true`.
 * @property containerName The name of the container that runs the database. Default is `vitess_test_db`.
 * @property debugStartup Whether to print debug logs during the startup process. Default is `false`.
 * @property enableDeclarativeSchemaChanges Whether to use declarative schema changes. Default is `false`.
 * @property enableInMemoryStorage Whether to use in-memory storage (tmpfs) for faster performance. Default is `false`.
 * @property enableScatters Whether to enable scatter queries, which fan out to all shards. Default is `true`.
 * @property inMemoryStorageSize The size of in-memory storage (tmpfs) if `enableInMemoryStorage` is `true` (e.g., "1G", "512M"). Default is "1024M".
 * @property keepAlive Whether to keep the database running after the test suite completes. Default is `true`.
 * @property mysqlVersion The MySQL version to use. Default is `8.0.36`.
 * @property port The port to connect to the database, which represents the vtgate. Default is `27003`.
 * @property schemaDir The location of vschema and SQL schema change files, which can be a classpath or filesystem path, which
 * is designated by the prefix `classpath:` or `filesystem:`. When using `classpath:`, `VitessTestDb` looks within `resources`.
 *
 * The expected input format of a schema directory looks like:
 * ```
 * /resources/vitess
 * ├── schema
 * │   ├── keyspace1
 * │   │   ├── v0001__add_table.sql
 * │   │   ├── vschema.json
 * │   ├── keyspace2
 * │   │   ├── v0002__add_table2.sql
 * │   │   ├── vschema.json
 * ```
 *
 * `VitessTestDb` will throw exceptions if an invalid directory structure is provided. The default value is `classpath:/vitess/schema`.
 *
 * @property sqlMode The server SQL mode. Defaults to the MySQL8 defaults:
 *   `ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION`.
 * @property transactionTimeoutSeconds The transaction timeout in seconds. Default is `null`.
 * @property vitessImage The Vitess image to be used. Open-source Vitess images can be found at
 *   [Docker Hub](https://hub.docker.com/r/vitess/vttestserver/tags). Default is `vitess/vttestserver:v21.0.4-mysql80`.
 * @property vitessVersion The Vitess major version to be used, which must match the version in `vitessImage`. Default
 *   is `21`.
 */
class VitessTestDb(
  private var autoApplySchemaChanges: Boolean = DefaultSettings.AUTO_APPLY_SCHEMA_CHANGES,
  val containerName: String = DefaultSettings.CONTAINER_NAME,
  private var debugStartup: Boolean = DefaultSettings.DEBUG_STARTUP,
  private var enableDeclarativeSchemaChanges: Boolean = DefaultSettings.ENABLE_DECLARATIVE_SCHEMA_CHANGES,
  private var enableInMemoryStorage: Boolean = DefaultSettings.ENABLE_IN_MEMORY_STORAGE,
  private var enableScatters: Boolean = DefaultSettings.ENABLE_SCATTERS,
  private var inMemoryStorageSize: String = DefaultSettings.IN_MEMORY_STORAGE_SIZE,
  private var keepAlive: Boolean = DefaultSettings.KEEP_ALIVE,
  private var lintSchema: Boolean = DefaultSettings.LINT_SCHEMA,
  private var mysqlVersion: String = DefaultSettings.MYSQL_VERSION,
  private var port: Int = DefaultSettings.PORT,
  private var schemaDir: String = DefaultSettings.SCHEMA_DIR,
  private var sqlMode: String = DefaultSettings.SQL_MODE,
  private var transactionIsolationLevel: TransactionIsolationLevel = DefaultSettings.TRANSACTION_ISOLATION_LEVEL,
  private var transactionTimeoutSeconds: Duration = DefaultSettings.TRANSACTION_TIMEOUT_SECONDS,
  private var vitessImage: String = DefaultSettings.VITESS_IMAGE,
  private var vitessVersion: Int = DefaultSettings.VITESS_VERSION,
) {
  private var isInitialized = false

  // Builder added for Java interoperability. This simulates how Kotlin constructors work, which use named parameters
  // with default arguments.
  class Builder {
    private var autoApplySchemaChanges: Boolean = DefaultSettings.AUTO_APPLY_SCHEMA_CHANGES
    private var containerName: String = DefaultSettings.CONTAINER_NAME
    private var debugStartup: Boolean = DefaultSettings.DEBUG_STARTUP
    private var enableDeclarativeSchemaChanges: Boolean = DefaultSettings.ENABLE_DECLARATIVE_SCHEMA_CHANGES
    private var enableInMemoryStorage: Boolean = DefaultSettings.ENABLE_IN_MEMORY_STORAGE
    private var enableScatters: Boolean = DefaultSettings.ENABLE_SCATTERS
    private var inMemoryStorageSize: String = DefaultSettings.IN_MEMORY_STORAGE_SIZE
    private var keepAlive: Boolean = DefaultSettings.KEEP_ALIVE
    private var lintSchema: Boolean = DefaultSettings.LINT_SCHEMA
    private var mysqlVersion: String = DefaultSettings.MYSQL_VERSION
    private var port: Int = DefaultSettings.PORT
    private var schemaDir: String = DefaultSettings.SCHEMA_DIR
    private var sqlMode: String = DefaultSettings.SQL_MODE
    private var transactionIsolationLevel: TransactionIsolationLevel = DefaultSettings.TRANSACTION_ISOLATION_LEVEL
    private var transactionTimeoutSeconds: Duration = DefaultSettings.TRANSACTION_TIMEOUT_SECONDS
    private var vitessImage: String = DefaultSettings.VITESS_IMAGE
    private var vitessVersion: Int = DefaultSettings.VITESS_VERSION

    fun autoApplySchemaChanges(autoApplySchemaChanges: Boolean) = apply {
      this.autoApplySchemaChanges = autoApplySchemaChanges
    }

    fun containerName(containerName: String) = apply { this.containerName = containerName }

    fun debugStartup(debugStartup: Boolean) = apply { this.debugStartup = debugStartup }

    fun enableDeclarativeSchemaChanges(enableDeclarativeSchemaChanges: Boolean) = apply {
      this.enableDeclarativeSchemaChanges = enableDeclarativeSchemaChanges
    }

    fun enableInMemoryStorage(enableInMemoryStorage: Boolean) = apply { this.enableInMemoryStorage = enableInMemoryStorage }

    fun enableScatters(enableScatters: Boolean) = apply { this.enableScatters = enableScatters }

    fun inMemoryStorageSize(inMemoryStorageSize: String) = apply { this.inMemoryStorageSize = inMemoryStorageSize }

    fun keepAlive(keepAlive: Boolean) = apply { this.keepAlive = keepAlive }

    fun lintSchema(lintSchema: Boolean) = apply { this.lintSchema = lintSchema }

    fun mysqlVersion(mysqlVersion: String) = apply { this.mysqlVersion = mysqlVersion }

    fun port(port: Int) = apply { this.port = port }

    fun schemaDir(schemaDir: String) = apply { this.schemaDir = schemaDir }

    fun sqlMode(sqlMode: String) = apply { this.sqlMode = sqlMode }

    fun transactionIsolationLevel(transactionIsolationLevel: TransactionIsolationLevel) = apply {
      this.transactionIsolationLevel = transactionIsolationLevel
    }

    fun transactionTimeoutSeconds(transactionTimeoutSeconds: Duration) = apply {
      this.transactionTimeoutSeconds = transactionTimeoutSeconds
    }

    fun vitessImage(vitessImage: String) = apply { this.vitessImage = vitessImage }

    fun vitessVersion(vitessVersion: Int) = apply { this.vitessVersion = vitessVersion }

    fun build(): VitessTestDb =
      VitessTestDb(
        autoApplySchemaChanges,
        containerName,
        debugStartup,
        enableDeclarativeSchemaChanges,
        enableInMemoryStorage,
        enableScatters,
        inMemoryStorageSize,
        keepAlive,
        lintSchema,
        mysqlVersion,
        port,
        schemaDir,
        sqlMode,
        transactionIsolationLevel,
        transactionTimeoutSeconds,
        vitessImage,
        vitessVersion,
      )
  }

  companion object {
    @JvmStatic fun Builder(): Builder = VitessTestDb.Builder()
  }

  /**
   * Start the Vitess database, which spins up a Docker container.
   *
   * @return [VitessTestDbStartupResult] on success startup, which contains information about the startup state.
   * @throws [VitessTestDbStartupException] on startup failures with a reason for the failure.
   */
  fun run(): VitessTestDbStartupResult {
    println("Starting VitessTestDb.")

    var containerStartResult: StartContainerResult

    val elapsed = measureTime {
      val container = getVitessDockerContainer()
      containerStartResult = container.start()

      if (containerStartResult.newContainerCreated) {
        println("🐳 Started new VitessTestDb Docker container `$containerName`.")
      } else {
        println("🐳 Reusing existing VitessTestDb Docker container `$containerName`.")
      }
    }

    isInitialized = true

    println("✅ VitessTestDB startup complete, took ${"%.2f".format(elapsed.inWholeMilliseconds / 1000.0)} seconds.")

    return VitessTestDbStartupResult(
      startupTimeMs = elapsed.inWholeMilliseconds,
      newContainerCreated = containerStartResult.newContainerCreated,
      newContainerReason = containerStartResult.newContainerReason,
      containerId = containerStartResult.containerId,
    )
  }

  /**
   * Shut down the running Vitess database, which removes the Docker container and used volumes.
   *
   * @return [VitessTestDbShutdownResult] which contains information about the shutdown state.
   */
  fun shutdown(): VitessTestDbShutdownResult {
    val container = getVitessDockerContainer()
    val shutdownResult = container.shutdown()

    return VitessTestDbShutdownResult(
      containerId = shutdownResult.containerId,
      containerRemoved = shutdownResult.containerRemoved,
    )
  }

  /**
   * Apply schema changes to the database on-demand. This is useful when `autoApplySchemaChanges` is disabled.
   *
   * @return [ApplySchemaResult] which contains information about schema changes that may have been processed, or a
   *   reason for being unable to process schema changes.
   */
  fun applySchema(): ApplySchemaResult = getVitessDockerContainer().applySchema()

  /**
   * Truncate all tables in all keyspaces except for sequence tables. The implementation leverages batched DELETE FROM
   * statements, which is significantly faster than using TRUNCATE (over 20x faster for a large amount of tables).
   */
  fun truncate() {
    try {
      val vitessDockerContainer = getVitessDockerContainer()
      vitessDockerContainer.queryExecutor.truncate()
    } catch (e: Exception) {
      throw VitessTestDbTruncateException("Failed to truncate tables", e)
    }
  }

  /**
   * Truncate all tables in all keyspaces except for sequence tables using an external vtgate port provided by the user.
   * This should only be used in scenarios where the `containerName` cannot be relied on to retrieve the
   * running Vitess Docker container, as default vtgate user credentials will be assumed.
   */
  fun truncate(userPort: Int) {
    try {
      val vitessQueryExecutor = VitessQueryExecutor(
        hostname,
        vtgatePort = userPort,
        vtgateUser = DefaultSettings.VTGATE_USER,
        vtgateUserPassword = DefaultSettings.VTGATE_USER_PASSWORD
      )
      vitessQueryExecutor.truncate()
    } catch (e: Exception) {
      throw VitessTestDbTruncateException("Failed to truncate tables", e)
    }
  }

  /**
   * Truncate all tables in all keyspaces except for sequence tables using an external connection, such as from an app
   * connection pool.
   */
  fun truncate(connection: Connection) {
    try {
      val vitessDockerContainer = getVitessDockerContainer()
      vitessDockerContainer.queryExecutor.truncate(connection)
    } catch (e: Exception) {
      throw VitessTestDbTruncateException("Failed to truncate tables", e)
    }
  }

  /**
   * Get the port used to debug query plans at {hostname}:/{query_debug_port}/debug/query_plans
   *
   * @return The port used for vtgate query plan debugging.
   */
  val queryPlanDebugPort: Int
    get() {
      val container = getVitessDockerContainer()
      return container.queryPlanDebugPort
    }

  /**
   * Get the exposed Docker port of the vtgate, which is used to connect to the Vitess database.
   *
   * @return The exposed Docker port of the vtgate.
   */
  val vtgatePort: Int
    get() {
      val container = getVitessDockerContainer()
      return container.vtgatePort
    }

  /**
   * Get the list of keyspaces that are present on the vtgate.
   *
   * @return A list of keyspace names as strings.
   */
  fun getKeyspaces(): List<String> {
    val vitessDockerContainer = getVitessDockerContainer()
    return vitessDockerContainer.queryExecutor.getKeyspaces()
  }

  /**
   * Get the list of tables for a specific keyspace.
   *
   * @param keyspace The name of the keyspace to retrieve tables from.
   *
   * @return A list of [VitessTable] objects representing the tables in the specified keyspace.
   */
  fun getTables(keyspace: String): List<VitessTable> {
    val vitessDockerContainer = getVitessDockerContainer()
    return vitessDockerContainer.queryExecutor.getTables(keyspace)
  }

  /**
   * Execute a SELECT SQL query against the vtgate.
   *
   * @param query The SQL query to execute.
   * @param target The target for the query, which defaults to "@primary". This can be used to specify a specific shard.
   *
   * @return A list of maps representing the rows returned by the query, where each map corresponds to a row and each
   *   key-value pair corresponds to a column name and its value.
   */
  fun executeQuery(query: String, target: String = "@primary"): List<Map<String, Any>> {
    val vitessDockerContainer = getVitessDockerContainer()
    return vitessDockerContainer.queryExecutor.executeQuery(query, target)
  }

  /**
   * Execute a SQL statement (i.e. INSERT, UPDATE, DELETE, DDL) against the vtgate.
   *
   * @param query The SQL statement to execute.
   * @param target The target for the query, which defaults to "@primary". This can be used to specify a specific shard.
   *
   * @return `true` if the execution was successful, `false` otherwise.
   */
  fun executeUpdate(query: String, target: String = "@primary"): Int {
    val vitessDockerContainer = getVitessDockerContainer()
    return vitessDockerContainer.queryExecutor.executeUpdate(query, target)
  }

  /**
   * Execute a transaction against the vtgate.
   *
   * @param query The SQL statement to execute as a transaction.
   * @param target The target for the query, which defaults to "@primary". This can be used to specify a specific shard.
   *
   * @return `true` if the transaction was successful, `false` otherwise.
   */
  fun executeTransaction(query: String, target: String = "@primary"): Boolean {
    val vitessDockerContainer = getVitessDockerContainer()
    return vitessDockerContainer.queryExecutor.executeTransaction(query, target)
  }

  private fun getVitessDockerContainer(): VitessDockerContainer {
    val container = VitessDockerContainer(
      autoApplySchemaChanges = autoApplySchemaChanges,
      containerName = containerName,
      debugStartup = debugStartup,
      enableDeclarativeSchemaChanges = enableDeclarativeSchemaChanges,
      enableInMemoryStorage = enableInMemoryStorage,
      enableScatters = enableScatters,
      inMemoryStorageSize = inMemoryStorageSize,
      keepAlive = keepAlive,
      lintSchema = lintSchema,
      mysqlVersion = mysqlVersion,
      schemaDir = schemaDir,
      sqlMode = sqlMode,
      transactionIsolationLevel = transactionIsolationLevel,
      transactionTimeoutSeconds = transactionTimeoutSeconds,
      userPort = port,
      vitessImage = vitessImage,
      vitessVersion = vitessVersion)

    return container
  }
}

/**
 * This class contains information about a successful run of VitessTestDb. If VitessTestDb fails to start up, a
 * [VitessTestDbStartupException] will be thrown instead.
 *
 * @property startupTimeMs The time it took to start the container in milliseconds.
 * @property containerId The ID of the Docker container that was started.
 * @property newContainerCreated Whether a new container was created or an existing one was reused.
 * @property newContainerReason The reason for creating a new container, if applicable.
 */
data class VitessTestDbStartupResult(
  val startupTimeMs: Long,
  override val containerId: String,
  override val newContainerCreated: Boolean,
  override val newContainerReason: String?,
) : StartContainerResult

/**
 * This class contains information about a shutdown operation of VitessTestDb, which attempts
 * to remove the container and its volumes.
 *
 * @property containerId The ID of the Docker container that was removed.
 * @property containerRemoved Whether the container was removed after.
 */
data class VitessTestDbShutdownResult(
  override val containerId: String?,
  override val containerRemoved: Boolean
) : RemoveContainerResult

interface StartContainerResult {
  val containerId: String
  val newContainerCreated: Boolean
  val newContainerReason: String?
}

interface RemoveContainerResult {
  val containerId: String?
  val containerRemoved: Boolean
}

open class VitessTestDbException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class VitessTestDbStartupException(message: String, cause: Throwable? = null) : VitessTestDbException(message, cause)

class VitessTestDbSchemaLintException(message: String, cause: Throwable? = null) :
  VitessTestDbException(message, cause)

class VitessTestDbSchemaParseException(message: String, cause: Throwable? = null) :
  VitessTestDbException(message, cause)

class VitessTestDbTruncateException(message: String, cause: Throwable? = null) : VitessTestDbException(message, cause)

data class ApplySchemaResult(
  val newContainerNeeded: Boolean,
  val newContainerNeededReason: String?,
  val schemaChangesProcessed: Boolean,
  val vschemaUpdates: List<VSchemaUpdate>,
  val ddlUpdates: List<DdlUpdate>,
)

data class VSchemaUpdate(val vschema: String, val keyspace: String)

data class DdlUpdate(val ddl: String, val keyspace: String)

data class VitessTable(val tableName: String, val type: VitessTableType)

enum class VitessTableType {
  SEQUENCE,
  REFERENCE,
  STANDARD,
}
