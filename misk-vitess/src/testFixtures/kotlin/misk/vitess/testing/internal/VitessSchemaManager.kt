package misk.vitess.testing.internal

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Image
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import misk.docker.withMiskDefaults
import misk.vitess.testing.ApplySchemaResult
import misk.vitess.testing.DdlUpdate
import misk.vitess.testing.DefaultSettings.VTCTLD_CLIENT_IMAGE
import misk.vitess.testing.VSchemaUpdate
import misk.vitess.testing.VitessTestDbStartupException
import wisp.resources.ClasspathResourceLoaderBackend
import wisp.resources.FilesystemLoaderBackend
import wisp.resources.ResourceLoader

/**
 * VitessSchemaManager is responsible for applying the schema defined in the schema directory. It validates the schema
 * directory contents, and applies the vschema and .sql DDL's for each keyspace.
 */
internal class VitessSchemaManager(
  private val containerName: String,
  private val debugStartup: Boolean,
  private val lintSchema: Boolean,
  private val schemaDir: String,
  private val enableDeclarativeSchemaChanges: Boolean,
  private val vitessClusterConfig: VitessClusterConfig,
) {
  private val dockerClient: DockerClient = setupDockerClient()
  private val skeema = VitessSkeema(vitessClusterConfig)
  private val currentSchemaDirPath: Path
  val keyspaces: List<VitessKeyspace>

  init {
    val supportedSchemaPrefixes = listOf(ClasspathResourceLoaderBackend.SCHEME, FilesystemLoaderBackend.SCHEME)
    val usesSupportedPrefix = supportedSchemaPrefixes.any { schemaDir.startsWith(it) }
    if (!usesSupportedPrefix) {
      throw VitessTestDbStartupException(
        "Schema directory `$schemaDir` must start with one of the supported prefixes: $supportedSchemaPrefixes"
      )
    }

    val tempSchemaDir = createTempSchemaDirectory()

    currentSchemaDirPath = tempSchemaDir
    keyspaces = VitessSchemaParser(lintSchema, schemaDir, tempSchemaDir).validateAndParse()
  }

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
      vitessQueryExecutor = VitessQueryExecutor(vitessClusterConfig)
    } catch (e: VitessQueryExecutorException) {
      return ApplySchemaResult(
        newContainerNeeded = true,
        newContainerNeededReason = "Failed to connect to vtgate running on port ${vitessClusterConfig.vtgatePort}.",
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

    // Run all keyspace operations in parallel to speed up schema change processing.
    val executorService = Executors.newFixedThreadPool(keyspaces.size)
    val futures = mutableListOf<Future<Unit>>()
    keyspaces.forEach { keyspace ->
      futures.add(
        executorService.submit<Unit> {
          val vschemaUpdate = processVschema(keyspace)
          vschemaUpdates.add(vschemaUpdate)

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
    executorService.shutdown()

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
    tables.forEach { vitessQueryExecutor.execute("DROP TABLE ${it.tableName};", keyspace.name) }

    keyspace.ddlCommands
      .sortedBy { it.first }
      .map { it.second }
      .forEach {
        vitessQueryExecutor.execute(query = it, target = keyspace.name)
        ddlUpdates.add(DdlUpdate(ddl = it, keyspace = keyspace.name))
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
            vitessQueryExecutor.execute(query, keyspace.name)
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
        "--server=${vitessClusterConfig.hostname}:${vitessClusterConfig.grpcPort}",
        "--vschema=${keyspace.vschema}",
      )

    printDebug("Applying vschema for keyspace `${keyspace.name}` with command: `${command.joinToString(" ")}`")
    executeDockerCommand(command, keyspace.name)
    return VSchemaUpdate(vschema = keyspace.vschema, keyspace = keyspace.name)
  }

  private fun applyDdlCommands(ddl: String, keyspace: VitessKeyspace, vitessQueryExecutor: VitessQueryExecutor) {
    printDebug("Applying schema changes:\n$ddl")
    vitessQueryExecutor.execute(ddl, keyspace.name)
  }

  private fun executeDockerCommand(command: List<String>, keyspace: String): String {
    val vtctldClientContainerName = "${containerName}_${keyspace}_vtctldclient"
    val existingContainer = findExistingContainer(vtctldClientContainerName)
    if (existingContainer != null) {
      dockerClient.removeContainerCmd(existingContainer.id).withForce(true).exec()
    }

    val createContainerResponse =
      dockerClient
        .createContainerCmd(VTCTLD_CLIENT_IMAGE)
        .withName(vtctldClientContainerName)
        .withHostConfig(HostConfig.newHostConfig().withNetworkMode("host"))
        .withCmd(command)
        .withAttachStdout(true)
        .withAttachStdin(true)
        .exec()

    dockerClient.startContainerCmd(createContainerResponse.id).exec()

    val statusCode = dockerClient.waitContainerCmd(createContainerResponse.id).start().awaitStatusCode()

    val logCallback = LogContainerResultCallback()
    dockerClient
      .logContainerCmd(createContainerResponse.id)
      .withStdOut(true)
      .withStdErr(true)
      .exec(logCallback)
      .awaitCompletion()

    printDebug("Vtctld response:\n${logCallback.getLogs()}")

    if (statusCode != 0) {
      throw VitessSchemaManagerException(
        "Failed to execute command: `${command.joinToString(" ")}`. Logs: ${logCallback.getLogs()}"
      )
    }

    return logCallback.getLogs()
  }

  private fun setupDockerClient(): DockerClient {
    val dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().withMiskDefaults().build()
    return DockerClientBuilder.getInstance(dockerClientConfig)
      .withDockerHttpClient(ApacheDockerHttpClient.Builder().dockerHost(dockerClientConfig.dockerHost).build())
      .build()
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
