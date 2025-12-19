package misk.vitess.testing.internal

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.exception.ConflictException
import com.github.dockerjava.api.exception.DockerClientException
import com.github.dockerjava.api.exception.InternalServerErrorException
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.exception.NotModifiedException
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HealthCheck
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Image
import com.github.dockerjava.api.model.Network
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.io.path.pathString
import misk.docker.withMiskDefaults
import misk.vitess.testing.ApplySchemaResult
import misk.vitess.testing.DefaultSettings.CONTAINER_PORT_BASE
import misk.vitess.testing.DefaultSettings.CONTAINER_PORT_MYSQL
import misk.vitess.testing.DefaultSettings.CONTAINER_PORT_VTGATE
import misk.vitess.testing.DefaultSettings.VITESS_DOCKER_NETWORK_NAME
import misk.vitess.testing.DefaultSettings.VITESS_DOCKER_NETWORK_TYPE
import misk.vitess.testing.DefaultSettings.VTGATE_USER
import misk.vitess.testing.DefaultSettings.VTGATE_USER_PASSWORD
import misk.vitess.testing.RemoveContainerResult
import misk.vitess.testing.StartContainerResult
import misk.vitess.testing.TransactionIsolationLevel
import misk.vitess.testing.VitessTestDbException
import misk.vitess.testing.VitessTestDbStartupException
import misk.vitess.testing.hostname

/** VitessDockerContainer validates user arguments and starts a Docker container with a vttestserver image. */
internal class VitessDockerContainer(
  private val autoApplySchemaChanges: Boolean,
  private val containerName: String,
  private val debugStartup: Boolean,
  private val enableDeclarativeSchemaChanges: Boolean,
  private val enableInMemoryStorage: Boolean,
  private val enableScatters: Boolean,
  private val inMemoryStorageSize: String,
  private val keepAlive: Boolean,
  private val lintSchema: Boolean,
  private val mysqlVersion: String,
  private val schemaDir: String,
  private val sqlMode: String,
  private val transactionIsolationLevel: TransactionIsolationLevel,
  private val transactionTimeoutSeconds: Duration,
  private val userPort: Int,
  private val vitessImage: String,
  private val vitessVersion: Int,
) {
  private companion object {
    const val ENABLE_DECLARATIVE_SCHEMA_CHANGES_ENV = "ENABLE_DECLARATIVE_SCHEMA_CHANGES"
    const val ENABLE_IN_MEMORY_STORAGE_ENV = "ENABLE_IN_MEMORY_STORAGE"
    const val ENABLE_SCATTERS_ENV = "ENABLE_SCATTERS"
    const val IN_MEMORY_STORAGE_SIZE_ENV = "IN_MEMORY_STORAGE_SIZE"
    const val KEEP_ALIVE_ENV = "KEEP_ALIVE"
    const val KEYSPACES_ENV = "KEYSPACES"
    const val MYSQL_VERSION_ENV = "MYSQL_VERSION"
    const val PORT_ENV = "PORT"
    const val SQL_MODE_ENV = "SQL_MODE"
    const val TRANSACTION_ISOLATION_LEVEL_ENV = "TRANSACTION_ISOLATION_LEVEL"
    const val TRANSACTION_TIMEOUT_SECONDS_ENV = "TRANSACTION_TIMEOUT_SECONDS"
    const val VITESS_IMAGE_ENV = "VITESS_IMAGE"
    const val VITESS_VERSION_ENV = "VITESS_VERSION"

    const val TEST_DB_DIR = "/testdb"
    const val REPLICA_COUNT =
      3 // Set to 3 to actually get 2 replicas, since the primary is inclusive in the count and we want 2 replicas.
    const val READONLY_COUNT =
      -1 // Set to -1 to actually set the read-only count to 0. This overrides a defaults issue in the vtcombo codebase.

    const val DBA_USER = "vt_dba_tcp_full"
    const val DBA_USER_PASSWORD = ""
    const val DOCKER_HEALTH_CHECK_HOST = "localhost"
    const val DOCKER_START_RETRIES = 6
    const val DOCKER_START_RETRY_DELAY_MS = 5000L
    const val CONTAINER_START_RETRIES = 10
    const val CONTAINER_START_RETRY_DELAY_MS = 10000L
    const val CONTAINER_HEALTH_CHECK_INTERVAL_SECONDS = 5L
    const val CONTAINER_HEALTH_CHECK_RETRIES = 10
    const val CONTAINER_STOP_TIMEOUT_SECONDS = 10
    const val PORT_CHECK_TIMEOUT_MS = 200

    // Retry configuration for container startup
    const val CONTAINER_STARTUP_MAX_RETRIES = 3
    const val CONTAINER_STARTUP_RETRY_DELAY_MS = 2000L

    // Used for synchronizing to prevent race-conditions.
    val containerLock = Any() // Use the same mutex for both starting and removing containers.
    val vitessNetworkLock = Any()
  }

  private val dockerClient: DockerClient = setupDockerClient()
  private val vitessMyCnf = VitessMyCnf(containerName, sqlMode, transactionIsolationLevel)

  fun start(): StartContainerResult {
    validateVitessVersionArgs()
    validateInMemoryStorageSize()

    val vitessSchemaPreparer = VitessSchemaPreparer(lintSchema, schemaDir)

    startDockerIfNotRunning()

    val shouldCreateContainerResult = shouldCreateContainer(containerName, vitessSchemaPreparer.keyspaces)

    if (!shouldCreateContainerResult.newContainerNeeded) {
      // At this point we can reuse the same container, but we also apply schema changes if any are present.
      if (autoApplySchemaChanges) {
        val applySchemaResult = getVitessSchemaApplier(vitessSchemaPreparer).applySchema()
        if (applySchemaResult.newContainerNeeded) {
          // In this state, we find we actually do need a new container, so proceed to start a new container.
          println("Starting new container `$containerName`.")
          if (applySchemaResult.newContainerNeededReason != null) {
            printDebug("Reason for new container: ${applySchemaResult.newContainerNeededReason}")
          }
          return startContainerWithRetries(vitessSchemaPreparer)
        }
      }

      return StartVitessContainerResult(
        newContainerCreated = false,
        containerId = shouldCreateContainerResult.existingContainerId.toString(),
      )
    }

    println("Starting new container `$containerName`.")
    if (shouldCreateContainerResult.newContainerReason != null) {
      printDebug("Reason for new container: ${shouldCreateContainerResult.newContainerReason}")
    }

    return startContainerWithRetries(vitessSchemaPreparer)
  }

  private fun startContainerWithRetries(vitessSchemaPreparer: VitessSchemaPreparer): StartContainerResult {
    var lastResult: StartDockerContainerResult? = null
    var containerId: String? = null

    repeat(CONTAINER_STARTUP_MAX_RETRIES) { attemptNumber ->
      try {
        val vitessPortConfig = VitessPortConfig.create(userPort)

        if (containerId == null) {
          stopExistingContainers(containerName, vitessPortConfig)
          containerId = createContainer(vitessPortConfig, vitessSchemaPreparer.keyspaces)
        }

        val startResult = startContainer(containerId)

        when {
          startResult.status == StartDockerContainerStatus.SUCCESS ||
            startResult.status == StartDockerContainerStatus.ALREADY_STARTED -> {
            waitForContainerHealth(containerId)
            createDbaUserForSideCarDb(containerId)

            if (autoApplySchemaChanges) {
              getVitessSchemaApplier(vitessSchemaPreparer).applySchema()
            }

            if (attemptNumber > 0) {
              println("Container startup succeeded on retry attempt ${attemptNumber + 1}")
            }

            return StartVitessContainerResult(
              newContainerCreated = true,
              newContainerReason =
                "Container started successfully" +
                  if (attemptNumber > 0) " after ${attemptNumber + 1} attempts" else "",
              containerId = containerId,
            )
          }

          startResult.shouldRetry -> {
            lastResult = startResult
            println("Container start attempt ${attemptNumber + 1}: ${startResult.message}")

            if (startResult.shouldUseNewPortConfig) {
              containerId = null // Force new container creation with new port config
            }
            // else keep same containerId for retry

            if (attemptNumber < CONTAINER_STARTUP_MAX_RETRIES - 1) {
              Thread.sleep(CONTAINER_STARTUP_RETRY_DELAY_MS * (attemptNumber + 1))
            }
          }

          else -> {
            // Non-retryable failure
            throw VitessTestDbStartupException(startResult.message ?: "Container startup failed", startResult.cause)
          }
        }
      } catch (e: Exception) {
        throw VitessTestDbStartupException("Unexpected error during container startup", e)
      }
    }

    // All retries exhausted
    throw VitessTestDbStartupException(
      "Failed to start container after `$CONTAINER_STARTUP_MAX_RETRIES` attempts. Last error: `${lastResult?.message}`",
      lastResult?.cause,
    )
  }

  fun shutdown(): RemoveContainerResult {
    val existingContainer = findExistingContainer(containerName)
    if (existingContainer != null) {
      printDebug("Shutting down container `$containerName`...")
      val removeResult = removeContainer(existingContainer)
      if (!removeResult) {
        println("Container `$containerName` is already shut down.")
        return RemoveVitessContainerResult(containerId = existingContainer.id, containerRemoved = false)
      }

      println("Container `$containerName` has been successfully shut down.")
      return RemoveVitessContainerResult(containerId = existingContainer.id, containerRemoved = true)
    }

    println("No container found with name `$containerName` to shut down.")
    return RemoveVitessContainerResult(containerId = null, containerRemoved = false)
  }

  fun applySchema(): ApplySchemaResult {
    val vitessSchemaPreparer = VitessSchemaPreparer(lintSchema, schemaDir)
    return getVitessSchemaApplier(vitessSchemaPreparer).applySchema()
  }

  val queryExecutor: VitessQueryExecutor
    get() {
      return VitessQueryExecutor(
        hostname = hostname,
        vtgatePort = vtgatePort,
        vtgateUser = VTGATE_USER,
        vtgateUserPassword = VTGATE_USER_PASSWORD,
      )
    }

  val queryPlanDebugPort: Int
    get() {
      return getMappedHostPort(CONTAINER_PORT_BASE)
    }

  val vtgatePort: Int
    get() {
      return getMappedHostPort(CONTAINER_PORT_VTGATE)
    }

  private val mysqlPort: Int
    get() {
      return getMappedHostPort(CONTAINER_PORT_MYSQL)
    }

  private val portMappings by lazy {
    val existingContainer =
      findExistingContainer(containerName)
        ?: throw VitessTestDbException("Container `$containerName` not found, unable to get host port mappings.")

    val inspect = dockerClient.inspectContainerCmd(existingContainer.id).exec()
    inspect.networkSettings.ports.bindings
  }

  private fun getMappedHostPort(containerPort: Int): Int {
    val exposedPort = ExposedPort.tcp(containerPort)
    val bindings: Array<Ports.Binding>? = portMappings[exposedPort]
    val hostPort =
      bindings?.firstOrNull()?.hostPortSpec
        ?: throw VitessTestDbException(
          "No host port mapped for container port `$containerPort` for container `$containerName`."
        )
    return hostPort.toInt()
  }

  private fun setupDockerClient(): DockerClient {
    val dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().withMiskDefaults().build()
    return DockerClientBuilder.getInstance(dockerClientConfig)
      .withDockerHttpClient(ApacheDockerHttpClient.Builder().dockerHost(dockerClientConfig.dockerHost).build())
      .build()
  }

  private fun validateVitessVersionArgs() {
    if (!enableScatters) {
      val requiredNoScatterVersion = 20
      require(vitessVersion >= requiredNoScatterVersion) {
        "Vitess image version must be >= $requiredNoScatterVersion" +
          " when scatters are disabled, found ${vitessVersion}."
      }
    }
  }

  private fun validateInMemoryStorageSize() {
    if (enableInMemoryStorage) {
      val pattern = Regex("""\d+[KMG]""")
      require(pattern.matches(inMemoryStorageSize.uppercase())) {
        "Invalid `inMemoryStorageSize`: `$inMemoryStorageSize`. Must match pattern '\\d+[KMG]', e.g., '1G', '512M', or '1024K'."
      }
    }
  }

  /**
   * This method checks if the container should be created based on the user arguments and the schema directory
   * contents.
   */
  private fun shouldCreateContainer(
    containerName: String,
    keyspaces: List<VitessKeyspace>,
  ): ShouldCreateVitessContainerResult {
    if (!keepAlive) {
      return ShouldCreateVitessContainerResult(newContainerNeeded = true, "the property `keepAlive` is `false`.")
    }

    // If a container already exists with a provided container name, return true if it is not healthy or not running.
    val existingContainer =
      findExistingContainer(containerName)
        ?: return ShouldCreateVitessContainerResult(
          newContainerNeeded = true,
          "a container for `$containerName` was not found.",
        )

    val containerInfo = dockerClient.inspectContainerCmd(existingContainer.id).exec()
    val isHealthy = containerInfo.state.health?.status == "healthy"
    if (!isHealthy) {
      return ShouldCreateVitessContainerResult(
        newContainerNeeded = true,
        "container `$containerName` was found but is not healthy.",
      )
    }

    if (!containerInfo.state.running!!) {
      return ShouldCreateVitessContainerResult(
        newContainerNeeded = true,
        "container `$containerName` was found but is not running.",
      )
    }

    // Otherwise we have a healthy and running container. We proceed to check if any user arguments have changed.
    val envVarsMap =
      containerInfo.config.env?.associate {
        val (key, value) = it.split("=")
        key to value
      }

    /**
     * This map represents user arguments that we want to check for changes. The key is the environment variable name in
     * Docker, and the value is a Pair of the current user argument value and the display name for that argument.
     * Intentionally leave off schemaDir from this map since we want to validate if the underlying schemaDir contents
     * changed.
     */
    val argsToValidate: Map<String, Pair<String, String>> =
      mapOf(
        ENABLE_DECLARATIVE_SCHEMA_CHANGES_ENV to
          ("$enableDeclarativeSchemaChanges" to "enableDeclarativeSchemaChanges"),
        ENABLE_IN_MEMORY_STORAGE_ENV to ("$enableInMemoryStorage" to "enableInMemoryStorage"),
        ENABLE_SCATTERS_ENV to ("$enableScatters" to "enableScatters"),
        IN_MEMORY_STORAGE_SIZE_ENV to (inMemoryStorageSize to "inMemoryStorageSize"),
        KEYSPACES_ENV to (getKeyspacesArgString(keyspaces) to "keyspaces"),
        MYSQL_VERSION_ENV to (mysqlVersion to "mysqlVersion"),
        PORT_ENV to ("${userPort}" to "port"),
        SQL_MODE_ENV to (sqlMode to "sqlMode"),
        TRANSACTION_ISOLATION_LEVEL_ENV to (transactionIsolationLevel.value to "transactionIsolationLevel"),
        TRANSACTION_TIMEOUT_SECONDS_ENV to ("${transactionTimeoutSeconds.seconds}" to "transactionTimeoutSeconds"),
        VITESS_IMAGE_ENV to (vitessImage to "vitessImage"),
        VITESS_VERSION_ENV to ("$vitessVersion" to "vitessVersion"),
      )

    val argUserValues = argsToValidate.mapValues { it.value.first }
    val argDisplayNames = argsToValidate.mapValues { it.value.second }

    for ((argName, argUserValue) in argUserValues) {
      val argValueInDocker = envVarsMap?.get(argName)
      if (argValueInDocker == null || argUserValue != argValueInDocker) {
        return ShouldCreateVitessContainerResult(
          newContainerNeeded = true,
          "VitessTestDb arguments have changed for argument `${argDisplayNames[argName]}`, last value: `$argValueInDocker`, new value: `$argUserValue`.",
        )
      }
    }

    return ShouldCreateVitessContainerResult(newContainerNeeded = false, existingContainerId = containerInfo.id)
  }

  private fun stopExistingContainers(containerName: String, vitessPortConfig: VitessPortConfig) {
    val existingContainer = findExistingContainer(containerName)

    existingContainer?.let { stopContainer(it) }

    // Also remove any containers that are using the same ports as the new container.
    vitessPortConfig.allHostPorts().forEach { port ->
      val containers = dockerClient.listContainersCmd().withShowAll(true).withFilter("publish", listOf("$port")).exec()
      containers.forEach { container ->
        stopContainer(container)
        if (isPortInUse(port)) {
          val containerName = container.names?.firstOrNull() ?: "unknown"
          println(
            "Port `$port` is still in use by container with id `${container.id}` and name `$containerName` after stopping container. Force removing container."
          )
          removeContainer(container)
        }
      }
    }

    // Ports can still be occupied by other processes.
    val occupiedPorts = vitessPortConfig.allHostPorts().filter { isPortInUse(it) }
    if (occupiedPorts.isNotEmpty()) {
      println("Ports `[${occupiedPorts.joinToString(", ")}]` are still in use.")
      throw VitessTestDbStartupException("Ports `[${occupiedPorts.joinToString(", ")}]` are in use by another process.")
    }
  }

  /** This method stops the container. If the container is already stopped or removed, it returns `false`. */
  private fun stopContainer(container: Container): Boolean {
    try {
      dockerClient.stopContainerCmd(container.id).withTimeout(CONTAINER_STOP_TIMEOUT_SECONDS).exec()
      return true
    } catch (e: NotFoundException) {
      // If we are in this state, the container is already removed.
      return false
    } catch (e: NotModifiedException) {
      // If we are in this state, the container is already stopped.
      return false
    }
  }

  private fun removeExistingContainers(containerName: String, vitessPortConfig: VitessPortConfig) {
    val existingContainer = findExistingContainer(containerName)

    existingContainer?.let { removeContainer(it) }

    // Also remove any containers that are using the same ports as the new container.
    vitessPortConfig.allHostPorts().forEach { port ->
      val containers = dockerClient.listContainersCmd().withShowAll(true).withFilter("publish", listOf("$port")).exec()
      containers.forEach { container -> removeContainer(container) }
    }
  }

  /** This method removes the container. If the container is already removed or being removed, it returns `false`. */
  private fun removeContainer(container: Container): Boolean {
    synchronized(containerLock) {
      try {
        dockerClient.removeContainerCmd(container.id).withForce(true).exec()
        return true
      } catch (e: NotFoundException) {
        // If we are in this state, the container is already removed.
        return false
      } catch (e: ConflictException) {
        // If we are in this state, the container is already being removed.
        return false
      }
    }
  }

  private fun startContainer(containerId: String): StartDockerContainerResult {
    synchronized(containerLock) {
      try {
        // Check if the container is already running
        val containerInfo = dockerClient.inspectContainerCmd(containerId).exec()
        if (containerInfo.state.running == true) {
          println("Container `$containerId` is already running. Skipping startContainer.")
          return StartDockerContainerResult.success("Container is already running.")
        }

        // Start the container
        dockerClient.startContainerCmd(containerId).exec()
        return StartDockerContainerResult.success("Container started successfully.")
      } catch (notModifiedException: NotModifiedException) {
        // The container is already started, ignore the exception.
        println("Container `$containerId` is already started.")
        return StartDockerContainerResult.alreadyStarted()
      } catch (notFoundException: NotFoundException) {
        return StartDockerContainerResult.retryableNewPortConfig(
          "Container for `$containerId` was not found during start up.",
          notFoundException,
        )
      } catch (internalServerException: InternalServerErrorException) {
        val message = internalServerException.message ?: ""

        return when {
          message.contains("port is already allocated") -> {
            handlePortAlreadyAllocatedError(containerId, internalServerException)
          }
          message.contains("OCI runtime create failed") || message.contains("device or resource busy") -> {
            StartDockerContainerResult.retryableSamePortConfig(
              "Failed to start Docker container for `$containerName`.",
              internalServerException,
            )
          }
          else -> {
            StartDockerContainerResult.nonRetryableFailure(
              "Failed to start Docker container for `$containerName`.",
              internalServerException,
            )
          }
        }
      }
    }
  }

  private fun waitForContainerHealth(containerId: String) {
    var retryCount = 0
    while (retryCount < CONTAINER_START_RETRIES) {
      try {
        val statusCode =
          dockerClient
            .waitContainerCmd(containerId)
            .start()
            .awaitStatusCode(CONTAINER_START_RETRY_DELAY_MS, TimeUnit.MILLISECONDS)

        // If we are here, we likely obtained a vttestserver start-up error.
        if (statusCode != 0) {
          if (debugStartup) {
            emitStartupLogs(containerId)
            throw VitessTestDbStartupException("Failed to start Docker container for `$containerName`.")
          }
          throw VitessTestDbStartupException(
            "Failed to start Docker container for `$containerName`, set `debugStartup` to `true` to see container startup logs."
          )
        }
      } catch (e: DockerClientException) {
        // Unfortunately vttestserver currently does not return a zero exit code when the container is ready.
        // So we intercept timeouts thrown via DockerClientException to check the health status of the container.
        val healthStatus = getContainerHealthStatus(containerId)
        if (healthStatus == "healthy") {
          println("Container `$containerName` is now healthy.")
          return
        }

        println("Waiting for container `$containerName` to start, current health status: `$healthStatus`")
        retryCount++
        if (retryCount == CONTAINER_START_RETRIES) {
          println("Failed to start Docker container for `$containerName` after max retries.")
          emitStartupLogs(containerId)
          throw VitessTestDbStartupException("Container health check failed after `$CONTAINER_START_RETRIES` attempts.")
        }
      }
    }
  }

  private fun emitStartupLogs(containerId: String) {
    val logCallback = LogContainerResultCallback()
    dockerClient.logContainerCmd(containerId).withStdOut(true).withStdErr(true).exec(logCallback).awaitCompletion()
    println("Container start up logs:\n${logCallback.getLogs()}")
  }

  private fun getContainerHealthStatus(containerId: String): String {
    val healthStatus: String
    try {
      healthStatus = dockerClient.inspectContainerCmd(containerId).exec().state.health.status
    } catch (e: NotFoundException) {
      throw VitessTestDbStartupException(
        "Container for id $containerId was not found during the health check, set `debugStartup` to `true` to see container startup logs."
      )
    }

    return healthStatus
  }

  private fun findExistingContainer(containerName: String): Container? {
    return dockerClient
      .listContainersCmd()
      .withShowAll(true)
      .withNameFilter(listOf("^/$containerName$"))
      .exec()
      .firstOrNull()
  }

  @Suppress("ALL")
  private fun createContainer(vitessPortConfig: VitessPortConfig, keyspaces: List<VitessKeyspace>): String {
    val images: List<Image> = dockerClient.listImagesCmd().withReferenceFilter(vitessImage).exec()
    if (images.isEmpty()) {
      println("Vitess image `$vitessImage` does not exist, proceeding to pull.")
      dockerClient.pullImageCmd(vitessImage).start().awaitCompletion()
    }

    val networkId = getOrCreateVitessNetwork()
    printDebug("Using Docker network `$networkId` for container `$containerName`.")

    val portBindings =
      vitessPortConfig.allPortMappings().map { portMapping ->
        PortBinding.parse("${portMapping.hostPort}:${portMapping.containerPort}")
      }
    val optionsFileDest = "$TEST_DB_DIR/my.cnf"

    val hostConfig =
      HostConfig().apply {
        withPortBindings(portBindings)
        withBinds(Bind(vitessMyCnf.optionsFilePath.pathString, Volume(optionsFileDest)))
        withNetworkMode(VITESS_DOCKER_NETWORK_NAME)
        withAutoRemove(
          !debugStartup // If `debugStartup` is `true`, we keep the container running to inspect logs.
        ) // Otherwise, remove container when it stops.
        if (enableInMemoryStorage) {
          /**
           * Tmpfs options using `/vt/vtdataroot` as the source to mount:
           * - "rw": Mount the filesystem as read-write.
           * - "noexec": Disallow execution of binaries from this mount for security.
           * - "nosuid": Ignore set-user-ID and set-group-ID bits on files for security.
           * - "mode=0777": Set the permissions of the /vt/vtdataroot mount point to be world-writable to ensure the
           *   vttestserver process can create directories and files within the tmpfs.
           * - "size=$inMemoryStorageSize": Limit the total size of the tmpfs to the value specified by the user (e.g.,
           *   "1G").
           */
          val tmpfsOptions = "rw,noexec,nosuid,mode=0777,size=${inMemoryStorageSize.uppercase()}"
          withTmpFs(mapOf("/vt/vtdataroot" to tmpfsOptions))
          printDebug("Enabled in-memory storage (tmpfs) for /vt/vtdataroot with options: $tmpfsOptions")
        }
      }

    // The health check is run from the Docker daemon, so it needs to target localhost.
    val healthCheck =
      HealthCheck()
        .withTest(
          listOf(
            "CMD-SHELL",
            "mysql -h $DOCKER_HEALTH_CHECK_HOST --protocol=tcp -P ${vitessPortConfig.vtgatePort.containerPort} -u=${VTGATE_USER} --execute 'USE @primary;'",
          )
        )
        .withInterval(Duration.ofSeconds(CONTAINER_HEALTH_CHECK_INTERVAL_SECONDS).toNanos())
        .withRetries(CONTAINER_HEALTH_CHECK_RETRIES)

    val cmd =
      mutableListOf(
        "/vt/bin/vttestserver",
        "--alsologtostderr",
        "--port",
        "${vitessPortConfig.basePort.containerPort}",
        "--mysql_bind_host",
        "0.0.0.0",
        "--keyspaces",
        getKeyspacesArgString(keyspaces),
        "--num_shards",
        getNumShardsArgString(keyspaces),
        "--extra_my_cnf",
        optionsFileDest,
        "--replica_count",
        "$REPLICA_COUNT",
        "--rdonly_count",
        "$READONLY_COUNT",
        "--foreign_key_mode",
        "disallow",
        "--vtcombo-bind-host",
        "0.0.0.0",
        "--queryserver-config-transaction-timeout",
        "${transactionTimeoutSeconds.seconds}s",
        "--mysql_server_version",
        "$mysqlVersion-Vitess",
      )

    if (!enableScatters) {
      cmd.add("--no_scatter")
    }

    try {
      return runCreateContainerCmd(hostConfig, healthCheck, cmd, vitessPortConfig, keyspaces)
    } catch (e: ConflictException) {
      // If we are in this state, a container already exists, possibly with older presets. We should
      // forcefully remove this container and try again.
      removeExistingContainers(containerName, vitessPortConfig)
      return runCreateContainerCmd(hostConfig, healthCheck, cmd, vitessPortConfig, keyspaces)
    }
  }

  private fun runCreateContainerCmd(
    hostConfig: HostConfig?,
    healthCheck: HealthCheck?,
    cmd: MutableList<String>,
    vitessPortConfig: VitessPortConfig,
    keyspaces: List<VitessKeyspace>,
  ): String {
    val container: CreateContainerResponse =
      dockerClient
        .createContainerCmd(vitessImage)
        .withName(containerName)
        .withHostConfig(hostConfig)
        .withPlatform("linux/amd64")
        .withHealthcheck(healthCheck)
        .withExposedPorts(*vitessPortConfig.allPortMappings().map { ExposedPort(it.containerPort) }.toTypedArray())
        .withEnv(
          "$ENABLE_DECLARATIVE_SCHEMA_CHANGES_ENV=$enableDeclarativeSchemaChanges",
          "$ENABLE_IN_MEMORY_STORAGE_ENV=$enableInMemoryStorage",
          "$ENABLE_SCATTERS_ENV=$enableScatters",
          "$IN_MEMORY_STORAGE_SIZE_ENV=$inMemoryStorageSize",
          "$KEEP_ALIVE_ENV=$keepAlive",
          "$KEYSPACES_ENV=${getKeyspacesArgString(keyspaces)}",
          "$MYSQL_VERSION_ENV=$mysqlVersion",
          "$PORT_ENV=${vitessPortConfig.vtgatePort.hostPort}",
          "$SQL_MODE_ENV=$sqlMode",
          "$TRANSACTION_ISOLATION_LEVEL_ENV=${transactionIsolationLevel.value}",
          "$TRANSACTION_TIMEOUT_SECONDS_ENV=${transactionTimeoutSeconds.seconds}",
          "$VITESS_IMAGE_ENV=$vitessImage",
          "$VITESS_VERSION_ENV=$vitessVersion",
        )
        .withCmd(cmd)
        .exec()
    return container.id
  }

  /**
   * This method checks if Docker is running and starts it if it is not running, which saves time on needing to manually
   * start Docker. This currently only supports MacOS and needs to be enhanced to support other platforms if we intend
   * to broaden usage.
   */
  private fun startDockerIfNotRunning() {
    try {
      dockerClient.pingCmd().exec()
    } catch (e: Exception) {
      if (e.cause is SocketException) {
        val osName = System.getProperty("os.name")
        if (osName.contains("Mac OS X")) {
          waitForDockerToStartOnMacOs()
        } else {
          throw VitessTestDbStartupException("Docker is not running, please start Docker.")
        }
      } else {
        throw VitessTestDbStartupException("An unexpected Docker startup error occurred.", e)
      }
    }
  }

  private fun waitForDockerToStartOnMacOs() {
    println("Docker is not running. Attempting to start Docker...")

    try {
      val process = ProcessBuilder(listOf("open", "-a", "docker")).redirectErrorStream(true).start()
      process.waitFor()
      process.exitValue()
    } catch (e: Exception) {
      throw VitessTestDbStartupException("Failed to open Docker on MacOS).", e)
    }

    repeat(DOCKER_START_RETRIES) { retryCount ->
      println("Waiting for Docker to start...")
      Thread.sleep(DOCKER_START_RETRY_DELAY_MS)
      try {
        dockerClient.pingCmd().exec()
        println("Docker has started.")
        return@waitForDockerToStartOnMacOs
      } catch (innerException: Exception) {
        if (innerException.cause !is SocketException) {
          throw VitessTestDbStartupException("An unexpected Docker startup error occurred.", innerException)
        }
      }
    }

    throw VitessTestDbStartupException("Failed to start Docker after $DOCKER_START_RETRIES attempts.")
  }

  private fun getKeyspacesArgString(keyspaces: List<VitessKeyspace>): String =
    keyspaces.map { it.name }.sorted().joinToString(",")

  private fun getNumShardsArgString(keyspaces: List<VitessKeyspace>): String =
    keyspaces.sortedBy { it.name }.joinToString(",") { it.shards.toString() }

  /**
   * Create a user that has TCP access to the sidecar db running on port - 1. Once we're on >= v20, we can leverage the
   * `initialize-with-vt-dba-tcp` flag and remove this logic (see https://github.com/vitessio/vitess/pull/15354)
   */
  private fun createDbaUserForSideCarDb(containerId: String) {
    // First step is to find the generated directory to access the MySQL socket file.
    val execListDir =
      dockerClient
        .execCreateCmd(containerId)
        .withAttachStderr(true)
        .withAttachStdout(true)
        .withCmd("ls", "/vt/vtdataroot")
        .exec()

    val listDirLogCallback = LogContainerResultCallback()
    dockerClient.execStartCmd(execListDir.id).exec(listDirLogCallback).awaitCompletion()

    if (dockerClient.inspectExecCmd(execListDir.id).exec().exitCodeLong != 0L) {
      throw VitessTestDbStartupException("Failed to list /vt/vtdataroot: ${listDirLogCallback.getLogs()}")
    }
    val vttestDir = listDirLogCallback.getLogs().trimEnd()

    // Now create the user using the socket file.
    val exec =
      dockerClient
        .execCreateCmd(containerId)
        .withAttachStderr(true)
        .withAttachStdout(true)
        .withCmd(
          "mysql",
          "-S",
          "/vt/vtdataroot/$vttestDir/vt_0000000001/mysql.sock",
          "-u",
          "root",
          "mysql",
          "-e",
          "CREATE USER '$DBA_USER'@'%'; GRANT ALL ON *.* TO '$DBA_USER'@'%';",
        )
        .exec()

    val logCallback = LogContainerResultCallback()
    dockerClient.execStartCmd(exec.id).exec(logCallback).awaitCompletion()

    val exitCode = dockerClient.inspectExecCmd(exec.id).exec().exitCodeLong
    if (exitCode != 0L) {
      throw VitessTestDbStartupException("Failed to create side car dba user: ${logCallback.getLogs()}")
    }
  }

  private fun getVitessSchemaApplier(vitessSchemaPreparer: VitessSchemaPreparer): VitessSchemaApplier {
    return VitessSchemaApplier(
      containerName = containerName,
      currentSchemaDirPath = vitessSchemaPreparer.currentSchemaDirPath,
      dbaUser = DBA_USER,
      dbaUserPassword = DBA_USER_PASSWORD,
      debugStartup = debugStartup,
      dockerClient = dockerClient,
      enableDeclarativeSchemaChanges = enableDeclarativeSchemaChanges,
      hostname = hostname,
      keyspaces = vitessSchemaPreparer.keyspaces,
      mysqlPort = mysqlPort,
      schemaDir = schemaDir,
      vtgatePort = vtgatePort,
      vtgateUser = VTGATE_USER,
      vtgateUserPassword = VTGATE_USER_PASSWORD,
    )
  }

  /** Create or get a network for Vitess docker containers to communicate with each other. */
  private fun getOrCreateVitessNetwork(): String {
    synchronized(vitessNetworkLock) {
      var existingNetwork = findExistingNetwork()

      if (existingNetwork == null) {
        try {
          val newNetworkId =
            dockerClient
              .createNetworkCmd()
              .withName(VITESS_DOCKER_NETWORK_NAME)
              .withDriver(VITESS_DOCKER_NETWORK_TYPE)
              .exec()
              .id
          return newNetworkId
        } catch (conflictException: ConflictException) {
          // If we are in this state, the network was already created.
          println("Network `$VITESS_DOCKER_NETWORK_NAME` was already created, attempting to find it.")
          existingNetwork = findExistingNetwork()
          if (existingNetwork == null) {
            throw VitessTestDbStartupException(
              "Network `$VITESS_DOCKER_NETWORK_NAME` was created but not found.",
              conflictException,
            )
          }
        }
      }

      // Validate the existing network we found has the expected network type.
      if (existingNetwork!!.driver != VITESS_DOCKER_NETWORK_TYPE) {
        throw VitessTestDbStartupException(
          "Network `$VITESS_DOCKER_NETWORK_NAME` exists for network id `${existingNetwork.id}` but is not of type `$VITESS_DOCKER_NETWORK_TYPE`. " +
            "Found type: `${existingNetwork.driver}`. Tear down the existing network to use this version of VitessTestDb."
        )
      }

      return existingNetwork.id
    }
  }

  private fun findExistingNetwork(): Network? {
    val networks = dockerClient.listNetworksCmd().exec()
    val existingNetwork = networks.find { it.name == VITESS_DOCKER_NETWORK_NAME }
    return existingNetwork
  }

  /**
   * This method handles an error that happens in CI environments where the port is already allocated by another
   * container or process, specifically errors of the form:
   *
   * com.github.dockerjava.api.exception.InternalServerErrorException: Status 500: {"message":"driver failed programming
   * external connectivity on endpoint <container_name> (<container_id>): Bind for 0.0.0.0:<port> failed: port is
   * already allocated"}
   *
   * Returns appropriate `StartDockerContainerResult` based on the port conflict scenario.
   */
  private fun handlePortAlreadyAllocatedError(
    containerId: String,
    internalServerException: Exception,
  ): StartDockerContainerResult {
    val message = internalServerException.message!!
    val allocatedPort = parseAllocatedPort(message)
    if (allocatedPort != null) {
      val containers =
        dockerClient.listContainersCmd().withShowAll(true).withFilter("publish", listOf(allocatedPort)).exec()
      val conflictingContainer = containers.firstOrNull()
      when {
        conflictingContainer == null -> {
          return StartDockerContainerResult.retryableSamePortConfig(
            "Port `$allocatedPort` is already allocated, but no container found using it.",
            internalServerException,
          )
        }
        conflictingContainer.id == containerId -> {
          println(
            "Port `$allocatedPort` is already allocated by the same container `$containerId`. Returning gracefully."
          )
          return StartDockerContainerResult.alreadyStarted()
        }
        else -> {
          val names = conflictingContainer.names?.joinToString() ?: "unknown"
          return StartDockerContainerResult.retryableNewPortConfig(
            "Port `$allocatedPort` is already allocated by container: `id=${conflictingContainer.id}`, `names=$names`.",
            internalServerException,
          )
        }
      }
    }

    return StartDockerContainerResult.nonRetryableFailure(
      "Failed to start Docker container for `$containerName` due to possible port conflict or race condition.",
      internalServerException,
    )
  }

  private fun parseAllocatedPort(message: String): String? {
    val portRegex = Regex("Bind for 0.0.0.0:(\\d+) failed: port is already allocated")
    val match = portRegex.find(message)
    return match?.groupValues?.get(1)
  }

  /**
   * Check if a port is in use via a socket connection. If there is a successful connection, it means the port is
   * already in use by another process; the socket is immediately closed after the connection is made via the "use
   * block", so it does not bind or occupy the port itself. If an exception is thrown, it means the port is not in use.
   */
  private fun isPortInUse(port: Int): Boolean {
    return try {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(hostname, port), PORT_CHECK_TIMEOUT_MS)
        true
      }
    } catch (e: SocketException) {
      false
    }
  }

  private fun printDebug(message: String) {
    if (debugStartup) {
      println(message)
    }
  }

  /** Result type for the Docker `startContainer` API operation itself that encodes a retry strategy as needed. */
  private data class StartDockerContainerResult(
    val status: StartDockerContainerStatus,
    val shouldRetry: Boolean = false,
    val shouldUseNewPortConfig: Boolean = false,
    val message: String? = null,
    val cause: Throwable? = null,
  ) {
    companion object {
      fun success(message: String? = null) =
        StartDockerContainerResult(status = StartDockerContainerStatus.SUCCESS, message = message)

      fun alreadyStarted() =
        StartDockerContainerResult(
          status = StartDockerContainerStatus.ALREADY_STARTED,
          message = "Container is already started",
        )

      fun retryableSamePortConfig(message: String, cause: Throwable? = null) =
        StartDockerContainerResult(
          status = StartDockerContainerStatus.FAILED,
          shouldRetry = true,
          shouldUseNewPortConfig = false,
          message = message,
          cause = cause,
        )

      fun retryableNewPortConfig(message: String, cause: Throwable? = null) =
        StartDockerContainerResult(
          status = StartDockerContainerStatus.FAILED,
          shouldRetry = true,
          shouldUseNewPortConfig = true,
          message = message,
          cause = cause,
        )

      fun nonRetryableFailure(message: String, cause: Throwable? = null) =
        StartDockerContainerResult(
          status = StartDockerContainerStatus.FAILED,
          shouldRetry = false,
          message = message,
          cause = cause,
        )
    }
  }

  private enum class StartDockerContainerStatus {
    SUCCESS,
    ALREADY_STARTED,
    FAILED,
  }
}

/**
 * This class contains information about whether a new Vitess container should be created or if an existing one should
 * be reused.
 *
 * @property newContainerNeeded Whether a new container should be created.
 * @property newContainerReason The reason for creating a new container, if applicable.
 * @property existingContainerId The ID of the existing container that should be reused, if applicable.
 */
data class ShouldCreateVitessContainerResult(
  val newContainerNeeded: Boolean,
  val newContainerReason: String? = null,
  val existingContainerId: String? = null,
)

/**
 * This class contains information about the result of starting a Vitess Docker container. If the Docker container fails
 * to start, a [VitessTestDbStartupException] will be thrown instead.
 *
 * @property containerId The ID of the Docker container that was started.
 * @property newContainerCreated Whether a new container was created or an existing one was reused.
 * @property newContainerReason The reason for creating a new container, if applicable.
 */
data class StartVitessContainerResult(
  override val containerId: String,
  override val newContainerCreated: Boolean,
  override val newContainerReason: String? = null,
) : StartContainerResult

/**
 * This class contains information about the result of removing a Vitess Docker container.
 *
 * @property containerId The ID of the Docker container that was removed.
 * @property containerRemoved Whether the container was removed.
 */
data class RemoveVitessContainerResult(override val containerId: String?, override val containerRemoved: Boolean) :
  RemoveContainerResult
