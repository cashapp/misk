package misk.vitess.testing

import misk.vitess.testing.internal.VitessClusterConfig
import misk.vitess.testing.internal.VitessDockerContainer
import misk.vitess.testing.internal.VitessQueryExecutor
import misk.vitess.testing.internal.VitessSchemaManager
import java.sql.Connection
import java.time.Duration
import kotlin.time.measureTime

/**
 * `VitessTestDb` is a class used to start a local Vitess database for tests.
 *
 * @property autoApplySchemaChanges Whether to automatically apply schema changes. Default is `true`.
 * @property containerName The name of the container that runs the database. Default is `vitess_test_db`.
 * @property debugStartup Whether to print debug logs during the startup process. Default is `false`.
 * @property enableScatters Whether to enable scatter queries, which fan out to all shards. Default is `true`.
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
 * @property enableDeclarativeSchemaChanges Whether to use declarative schema changes. Default is `false`.
 * @property vitessImage The Vitess image to be used. Open-source Vitess images can be found at
 *   [Docker Hub](https://hub.docker.com/r/vitess/vttestserver/tags). Default is `vitess/vttestserver:v19.0.9-mysql80`.
 * @property vitessVersion The Vitess major version to be used, which must match the version in `vitessImage`. Default
 *   is `19`.
 */
class VitessTestDb(
  private var autoApplySchemaChanges: Boolean = DefaultSettings.AUTO_APPLY_SCHEMA_CHANGES,
  private var containerName: String = DefaultSettings.CONTAINER_NAME,
  private var debugStartup: Boolean = DefaultSettings.DEBUG_STARTUP,
  private var enableDeclarativeSchemaChanges: Boolean = DefaultSettings.ENABLE_DECLARATIVE_SCHEMA_CHANGES,
  private var enableScatters: Boolean = DefaultSettings.ENABLE_SCATTERS,
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
  private val vitessClusterConfig = VitessClusterConfig(port)
  private val vitessSchemaManager by lazy {
    VitessSchemaManager(
      containerName = containerName,
      debugStartup = debugStartup,
      lintSchema = lintSchema,
      schemaDir = schemaDir,
      enableDeclarativeSchemaChanges = enableDeclarativeSchemaChanges,
      vitessClusterConfig = vitessClusterConfig,
    )
  }
  private var isInitialized = false

  // Builder added for Java interoperability. This simulates how Kotlin constructors work, which use named parameters
  // with default arguments.
  class Builder {
    private var autoApplySchemaChanges: Boolean = DefaultSettings.AUTO_APPLY_SCHEMA_CHANGES
    private var containerName: String = DefaultSettings.CONTAINER_NAME
    private var debugStartup: Boolean = DefaultSettings.DEBUG_STARTUP
    private var enableDeclarativeSchemaChanges: Boolean = DefaultSettings.ENABLE_DECLARATIVE_SCHEMA_CHANGES
    private var enableScatters: Boolean = DefaultSettings.ENABLE_SCATTERS
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

    fun enableScatters(enableScatters: Boolean) = apply { this.enableScatters = enableScatters }

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
        enableScatters,
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

      if (autoApplySchemaChanges) {
        vitessSchemaManager.applySchema()
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
  fun applySchema(): ApplySchemaResult = vitessSchemaManager.applySchema()

  /**
   * Truncate all tables in all keyspaces except for sequence tables. The implementation leverages batched DELETE FROM
   * statements, which is significantly faster than using TRUNCATE (over 20x faster for a large amount of tables).
   */
  fun truncate() {
    var vitessQueryExecutor: VitessQueryExecutor
    try {
      vitessQueryExecutor = VitessQueryExecutor(vitessClusterConfig)
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
    var vitessQueryExecutor: VitessQueryExecutor
    try {
      vitessQueryExecutor = VitessQueryExecutor(vitessClusterConfig)
      vitessQueryExecutor.truncate(connection)
    } catch (e: Exception) {
      throw VitessTestDbTruncateException("Failed to truncate tables", e)
    }
  }

  private fun getVitessDockerContainer(): VitessDockerContainer {
    val container =
      VitessDockerContainer(
        containerName,
        debugStartup,
        enableScatters,
        keepAlive,
        mysqlVersion,
        sqlMode,
        transactionIsolationLevel,
        transactionTimeoutSeconds,
        vitessClusterConfig,
        vitessImage,
        vitessSchemaManager,
        vitessVersion,
      )
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
