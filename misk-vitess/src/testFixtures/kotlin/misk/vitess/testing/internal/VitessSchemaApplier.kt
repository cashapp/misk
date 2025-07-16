package misk.vitess.testing.internal

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.exception.DockerClientException
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Image
import misk.vitess.testing.ApplySchemaResult
import misk.vitess.testing.DdlUpdate
import misk.vitess.testing.DefaultSettings.CONTAINER_PORT_GRPC
import misk.vitess.testing.DefaultSettings.VITESS_DOCKER_NETWORK_NAME
import misk.vitess.testing.DefaultSettings.VTCTLD_CLIENT_IMAGE
import misk.vitess.testing.VSchemaUpdate
import misk.vitess.testing.VitessTableType
import misk.vitess.testing.VitessTestDbStartupException
import misk.resources.ClasspathResourceLoaderBackend
import misk.resources.FilesystemLoaderBackend
import misk.resources.ResourceLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

/**
 * VitessSchemaApplier is responsible for applying the vschema and .sql DDL's for each keyspace in the schema directory.
 */
internal class VitessSchemaApplier(
  private val containerName: String,
  private val currentSchemaDirPath: Path,
  private val dbaUser: String,
  private val dbaUserPassword: String,
  private val debugStartup: Boolean,
  private val dockerClient: DockerClient,
  private val enableDeclarativeSchemaChanges: Boolean,
  private val keyspaces: List<VitessKeyspace>,
  private val hostname: String,
  private val mysqlPort: Int,
  private val schemaDir: String,
  private val vtgatePort: Int,
  private val vtgateUser: String,
  private val vtgateUserPassword: String
) {
  private companion object {
    const val VTCTLDCLIENT_CONTAINER_START_DELAY_MS = 10000L
    const val VTCTLDCLIENT_APPLY_VSCHEMA_TIMEOUT_MS = "10000ms"
  }

  private val skeema = VitessSkeema(
    hostname = hostname,
    mysqlPort = mysqlPort,
    dbaUser = dbaUser,
    dbaUserPassword = dbaUserPassword)


  /**
   * Apply the schema defined in the schema directory, which will reinitialize the keyspace vschema's and apply all
   * DDL's. If no schema changes are detected, this method will return false and not apply any changes.
   *
   * @
   */
  fun applySchema(): ApplySchemaResult {
    // Use VitessQueryExecutor to check if container is running. If not, defer to the caller
    // to handle starting a new container.
    var vitessQueryExecutor: VitessQueryExecutor
    try {
      vitessQueryExecutor = VitessQueryExecutor(
        hostname = hostname,
        vtgatePort = vtgatePort,
        vtgateUser = vtgateUser,
        vtgateUserPassword = vtgateUserPassword)

    } catch (e: VitessQueryExecutorException) {
      return ApplySchemaResult(
        newContainerNeeded = true,
        newContainerNeededReason = "Failed to connect to vtgate running on port ${vtgatePort}.",
        schemaChangesProcessed = false,
        vschemaUpdates = emptyList(),
        ddlUpdates = emptyList(),
      )
    }

    // First validate that the keyspaces are the same. If the keyspace changes,
    // we need to restart the container for now to stand-up new tablets, in which
    // we'll defer to the caller to handle that.
    val keyspaceNamesFromVtGate = vitessQueryExecutor.getKeyspaces()
    val userSchemaKeyspaces = keyspaces.map { it.name }.sorted()
    val missingKeyspaces = userSchemaKeyspaces - keyspaceNamesFromVtGate
    val extraKeyspaces = keyspaceNamesFromVtGate - userSchemaKeyspaces

    if (keyspaceNamesFromVtGate.sorted() != keyspaces.map { it.name }.sorted()) {
      return ApplySchemaResult(
        newContainerNeeded = true,
        newContainerNeededReason =
          "Keyspaces in schema directory do not match keyspaces in vtgate. Missing keyspaces in vtgate: $missingKeyspaces, extra keyspacess in vtgate: $extraKeyspaces.",
        schemaChangesProcessed = false,
        vschemaUpdates = emptyList(),
        ddlUpdates = emptyList(),
      )
    }

    // We now check if the current schema directory is different from the last schema directory based on file contents.
    // The last schema directory in this case is stored in /tmp/vitess-test-db/container_id/schema, where container_id
    // references the current container. If the files completely match, we can opt out early, as this will save on calls
    // to the vtctld and vtgate.
    val lastSchemaDirPath = getLastSchemaDir()
    val schemaDirectoryDiff = checkSchemaDirectoryContentsChanged(lastSchemaDirPath)

    if (schemaDirectoryDiff.lastSchemaDirFound && !schemaDirectoryDiff.diffsFound) {
      printDebug("No schema changes to apply.")
      initializeSequenceTables(vitessQueryExecutor)
      return ApplySchemaResult(
        newContainerNeeded = false,
        newContainerNeededReason = null,
        schemaChangesProcessed = false,
        vschemaUpdates = emptyList(),
        ddlUpdates = emptyList(),
      )
    }

    if (schemaDirectoryDiff.diffsFound && debugStartup) {
      schemaDirectoryDiff.schemaDirectoryDiffs.forEach { (keyspace, diffs) ->
        diffs.forEach { diff -> printDebug(diff) }
      }
    }

    prepareVtctldClientImage()

    var schemaChangesProcessed = AtomicBoolean(false)
    val ddlUpdates = ConcurrentLinkedQueue<DdlUpdate>()
    val vschemaUpdates = ConcurrentLinkedQueue<VSchemaUpdate>()

    // Apply VSchemas in parallel but wait for all to complete before proceeding to DDL's.
    val vschemaExecutorService = Executors.newFixedThreadPool(keyspaces.size)
    val vschemaFutures = mutableListOf<Future<VSchemaUpdate>>()
    
    keyspaces.forEach { keyspace ->
      vschemaFutures.add(
        vschemaExecutorService.submit<VSchemaUpdate> {
          processVschema(keyspace)
        }
      )
    }

    vschemaFutures.forEach { future ->
      vschemaUpdates.add(future.get())
    }
    vschemaExecutorService.shutdown()

    val ddlExecutorService = Executors.newFixedThreadPool(keyspaces.size)
    val futures = mutableListOf<Future<Unit>>()
    keyspaces.forEach { keyspace ->
      futures.add(
        ddlExecutorService.submit<Unit> {
          var schemaChangesProcessedForKeyspace: Boolean
          if (enableDeclarativeSchemaChanges) {
            schemaChangesProcessedForKeyspace = applyDeclarativeSchemaChanges(keyspace, vitessQueryExecutor, ddlUpdates)
          } else {
            schemaChangesProcessedForKeyspace =
              applyTraditionalSchemaChanges(keyspace, vitessQueryExecutor, schemaDirectoryDiff, ddlUpdates)
          }

          if (schemaChangesProcessedForKeyspace) {
            schemaChangesProcessed.set(true)
          }
        }
      )
    }

    futures.forEach { it.get() }
    ddlExecutorService.shutdown()

    initializeSequenceTables(vitessQueryExecutor)
    println("🔧 Schema is applied.")

    saveSchemaDirectory(schemaChangesProcessed.get(), lastSchemaDirPath)

    return ApplySchemaResult(
      newContainerNeeded = false,
      newContainerNeededReason = null,
      schemaChangesProcessed = schemaChangesProcessed.get(),
      vschemaUpdates = vschemaUpdates.toList(),
      ddlUpdates = ddlUpdates.toList(),
    )
  }

  private fun saveSchemaDirectory(schemaChangesProcessed: Boolean, lastSchemaDirPath: Path) {
    if (schemaChangesProcessed) {
      // Replace /tmp/{container_id}/schema with the current schema directory in currentSchemaDir
      if (Files.exists(lastSchemaDirPath)) {
        lastSchemaDirPath.toFile().deleteRecursively()
      }

      Files.walk(currentSchemaDirPath).forEach { sourcePath ->
        val targetPath = lastSchemaDirPath.resolve(currentSchemaDirPath.relativize(sourcePath))
        if (Files.isDirectory(sourcePath)) {
          Files.createDirectories(targetPath)
        } else {
          Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
        }
      }
    }
    // Finally delete the temporary schema directory, since lastSchemaDirPath has the up-to-date schema directory.
    currentSchemaDirPath.toFile().deleteRecursively()
  }

  private fun applyDeclarativeSchemaChanges(
    keyspace: VitessKeyspace,
    vitessQueryExecutor: VitessQueryExecutor,
    ddlUpdates: ConcurrentLinkedQueue<DdlUpdate>,
  ): Boolean {
    val skeemaDiff = skeema.diff(keyspace)
    if (skeemaDiff.hasDiff) {
      printDebug("Schema changes detected for keyspace `${keyspace.name}`")
      if (skeemaDiff.diff != null) {
        applyDdlCommands(skeemaDiff.diff, keyspace, vitessQueryExecutor)
        ddlUpdates.add(DdlUpdate(keyspace.name, skeemaDiff.diff))
        return true
      } else {
        throw VitessSchemaManagerException("Skeema diff returned null diff for keyspace `${keyspace.name}`")
      }
    } else {
      printDebug("SQL schema for keyspace `${keyspace.name}` is up-to-date.")
      return false
    }
  }

  private fun applyTraditionalSchemaChanges(
    keyspace: VitessKeyspace,
    vitessQueryExecutor: VitessQueryExecutor,
    schemaDirectoryDiff: SchemaDirectoryDiff,
    ddlUpdates: ConcurrentLinkedQueue<DdlUpdate>,
  ): Boolean {
    val schemaDirDiffsForKeyspace = schemaDirectoryDiff.schemaDirectoryDiffs[keyspace.name]
    if (schemaDirectoryDiff.lastSchemaDirFound && schemaDirDiffsForKeyspace!!.isEmpty()) {
      printDebug("No schema changes detected for keyspace `${keyspace.name}`")
      return false
    }

    val tables = vitessQueryExecutor.getTables(keyspace.name)
    tables.forEach { vitessQueryExecutor.executeUpdate("DROP TABLE ${it.tableName};", keyspace.name) }

    keyspace.ddlCommands
      .sortedBy { it.first }
      .map { it.second }
      .forEach { ddl ->
        vitessQueryExecutor.executeUpdateWithRetries(ddl, keyspace.name)
        ddlUpdates.add(DdlUpdate(ddl = ddl, keyspace = keyspace.name))
      }

    return true
  }

  /** Initialize sequence tables for non-sharded keyspaces. */
  private fun initializeSequenceTables(vitessQueryExecutor: VitessQueryExecutor) {
    keyspaces
      .filter { !it.sharded }
      .forEach { keyspace ->
        keyspace.tables
          .filter { it.type == VitessTableType.SEQUENCE }
          .forEach { table ->
            val query = "INSERT IGNORE INTO `${table.tableName}` (id, next_id, cache) VALUES (0, 1, 1000);"
            if (debugStartup) {
              printDebug(
                "Initializing sequence table `${table.tableName}` in keyspace `${keyspace.name}` with query: $query"
              )
            }
            vitessQueryExecutor.executeUpdate(query, keyspace.name)
          }
      }
  }

  /**
   * Apply the vschema for the keyspace. It's faster to just apply the vschema each time then to also check the vschema
   * for changes.
   */
  private fun processVschema(keyspace: VitessKeyspace): VSchemaUpdate {
    val command =
      listOf(
        "vtctldclient",
        "ApplyVSchema",
        keyspace.name,
        "--strict",
        "--action_timeout=$VTCTLDCLIENT_APPLY_VSCHEMA_TIMEOUT_MS",
        "--server=$containerName:${CONTAINER_PORT_GRPC}",
        "--vschema=${keyspace.vschema}",
      )

    printDebug("Applying vschema for keyspace `${keyspace.name}` with command: `${command.joinToString(" ")}`")
    executeDockerCommand(command, keyspace.name)
    return VSchemaUpdate(vschema = keyspace.vschema, keyspace = keyspace.name)
  }

  private fun applyDdlCommands(ddl: String, keyspace: VitessKeyspace, vitessQueryExecutor: VitessQueryExecutor) {
    printDebug("Applying schema changes:\n$ddl")
    vitessQueryExecutor.executeUpdateWithRetries(ddl, keyspace.name)
  }

  private fun executeDockerCommand(command: List<String>, keyspace: String): String {
    val vtctldClientContainerName = "${containerName}_${keyspace}_vtctldclient"
    val existingContainer = findExistingContainer(vtctldClientContainerName)
    if (existingContainer != null) {
      printDebug("Found existing vtctldclient container, proceeding to remove.")
      dockerClient.removeContainerCmd(existingContainer.id).withForce(true).exec()
    }

    val networks = dockerClient.listNetworksCmd().exec()
    networks.find { it.name == VITESS_DOCKER_NETWORK_NAME}
      ?: throw VitessSchemaManagerException("VitessSchemaManager could not find the correct Docker Network named `$VITESS_DOCKER_NETWORK_NAME`. The network may have failed to initialize or VitessDockerContainer.createContainer may not have been run.")

    printDebug("Creating new vtctldclient container.")
    val createContainerResponse =
      dockerClient
        .createContainerCmd(VTCTLD_CLIENT_IMAGE)
        .withName(vtctldClientContainerName)
        .withHostConfig(HostConfig.newHostConfig().withNetworkMode(VITESS_DOCKER_NETWORK_NAME))
        .withCmd(command)
        .withAttachStdout(true)
        .withAttachStdin(true)
        .exec()
    printDebug("Created vtctldclient container with id `${createContainerResponse.id}`")

    dockerClient.startContainerCmd(createContainerResponse.id).exec()

    try {
      val statusCode = dockerClient
        .waitContainerCmd(createContainerResponse.id)
        .start()
        .awaitStatusCode(VTCTLDCLIENT_CONTAINER_START_DELAY_MS, TimeUnit.MILLISECONDS)
      printDebug("vtctldclient container command wait status code: `$statusCode`")

      val containerLogs = getContainerLogs(createContainerResponse.id)
      printDebug("Vtctld response:\n$containerLogs")

      if (statusCode != 0) {
        throw VitessSchemaManagerException(
          "Failed to execute command: `${command.joinToString(" ")}`. Logs: $containerLogs}"
        )
      }

      return containerLogs
    } catch (e: DockerClientException) {
      val containerLogs = getContainerLogs(createContainerResponse.id)
      println("Failed to await vtctld container command for `${command.joinToString(" ")}`. Logs: $containerLogs")
      throw VitessSchemaManagerException(
        "Failed to await vtctld container command: `${command.joinToString(" ")}`, check logs for details.", e)
    } finally {
      dockerClient.removeContainerCmd(createContainerResponse.id).withForce(true).exec()
    }
  }


  private fun createTempSchemaDirectory(): Path {
    val tempDir = Files.createTempDirectory("schema-")

    val resourceLoader = ResourceLoader(
      mapOf(
        ClasspathResourceLoaderBackend.SCHEME to ClasspathResourceLoaderBackend,
        FilesystemLoaderBackend.SCHEME to FilesystemLoaderBackend
      )
    )

    tempDir.createDirectories()

    if (!resourceLoader.exists(schemaDir)) {
      throw VitessTestDbStartupException("Schema directory `$schemaDir` does not exist")
    }

    resourceLoader.copyTo(schemaDir, tempDir)

    return tempDir
  }

  /**
   * Check if the user supplied schema directory is different from the last schema directory used.
   *
   * @return a map of keyspace to schema differences if the schema directory has changed, otherwise return an empty map.
   */
  private fun checkSchemaDirectoryContentsChanged(lastSchemaDirPath: Path): SchemaDirectoryDiff {
    if (!Files.exists(lastSchemaDirPath)) {
      return SchemaDirectoryDiff(diffsFound = false, schemaDirectoryDiffs = emptyMap(), lastSchemaDirFound = false)
    }

    val keyspaces =
      Files.list(lastSchemaDirPath).filter { Files.isDirectory(it) }.map { it.fileName.toString() }.sorted().toList()

    val schemaDirectoryDiffs = mutableMapOf<String, List<String>>()
    var diffsPresent = false

    keyspaces.forEach { keyspace ->
      val schemaDiffsForKeyspace = mutableListOf<String>()

      val lastSchemaFiles = getFilesForKeyspace(lastSchemaDirPath.pathString, keyspace)
      val newSchemaFiles = getFilesForKeyspace(currentSchemaDirPath.pathString, keyspace)

      val currentOnlySchemaFiles = mutableListOf<String>()
      val newOnlySchemaFiles = mutableListOf<String>()
      val differingSchemaFiles = mutableListOf<String>()

      lastSchemaFiles.forEach { hostFile ->
        val containerFile = newSchemaFiles.find { it.first == hostFile.first }
        if (containerFile == null) {
          currentOnlySchemaFiles.add(hostFile.first)
        } else if (hostFile.second != containerFile.second) {
          differingSchemaFiles.add(hostFile.first)
        }
      }

      newSchemaFiles.forEach { containerFile ->
        if (lastSchemaFiles.none { it.first == containerFile.first }) {
          newOnlySchemaFiles.add(containerFile.first)
        }
      }

      if (currentOnlySchemaFiles.isNotEmpty()) {
        schemaDiffsForKeyspace.add(
          "Files present in running container but not in user schema `$schemaDir` for keyspace `$keyspace`: $currentOnlySchemaFiles"
        )
      }

      if (newOnlySchemaFiles.isNotEmpty()) {
        schemaDiffsForKeyspace.add(
          "Files present in user schema `$schemaDir` but not in running container for keyspace `$keyspace`: $newOnlySchemaFiles"
        )
      }

      if (differingSchemaFiles.isNotEmpty()) {
        schemaDiffsForKeyspace.add("Files with different content in keyspace `$keyspace`: $differingSchemaFiles")
      }

      schemaDirectoryDiffs[keyspace] = schemaDiffsForKeyspace
      if (schemaDiffsForKeyspace.isNotEmpty()) {
        diffsPresent = true
      }
    }

    return SchemaDirectoryDiff(diffsPresent, schemaDirectoryDiffs = schemaDirectoryDiffs, lastSchemaDirFound = true)
  }

  private fun getLastSchemaDir(): Path {
    // Look for the last schema directory state in /tmp/vitess-test-db/container_id/schema
    val container =
      findExistingContainer(containerName)
        ?: throw VitessSchemaManagerException("Unable to find container with name `$containerName`.")

    val lastSchemaDirPath = Paths.get("/tmp/vitess-test-db/${container.id}/schema")
    return lastSchemaDirPath
  }

  private fun getFilesForKeyspace(currentSchemaDirPath: String, keyspace: String?) =
    Files.walk(Paths.get(currentSchemaDirPath, keyspace))
      .filter { Files.isRegularFile(it) }
      .map { it.fileName.toString() to it.toFile().readText() }
      .toList()
      .sortedBy { it.first }

  private fun prepareVtctldClientImage() {
    val images: List<Image> = dockerClient.listImagesCmd().withReferenceFilter(VTCTLD_CLIENT_IMAGE).exec()
    if (images.isEmpty()) {
      printDebug("vtctldclient image `$VTCTLD_CLIENT_IMAGE` does not exist, proceeding to pull.")
      dockerClient.pullImageCmd(VTCTLD_CLIENT_IMAGE).start().awaitCompletion()
    }
  }

  private fun findExistingContainer(containerName: String): Container? {
    return dockerClient
      .listContainersCmd()
      .withShowAll(true)
      .withNameFilter(listOf("^/$containerName$"))
      .exec()
      .firstOrNull()
  }

  private fun getContainerLogs(containerId: String): String {
    val logCallback = LogContainerResultCallback()
    dockerClient
      .logContainerCmd(containerId)
      .withStdOut(true)
      .withStdErr(true)
      .exec(logCallback)
      .awaitCompletion()

    return logCallback.getLogs()
  }

  private fun printDebug(message: String) {
    if (debugStartup) {
      println(message)
    }
  }
}

internal data class SchemaDirectoryDiff(
  val diffsFound: Boolean,
  val schemaDirectoryDiffs: Map<String, List<String>>,
  val lastSchemaDirFound: Boolean,
)

class VitessSchemaManagerException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
