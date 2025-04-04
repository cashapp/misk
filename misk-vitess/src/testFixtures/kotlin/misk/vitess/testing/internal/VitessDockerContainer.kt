package misk.vitess.testing.internal

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.exception.ConflictException
import com.github.dockerjava.api.exception.DockerClientException
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.exception.NotModifiedException
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HealthCheck
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Image
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import java.net.SocketException
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.io.path.pathString
import misk.docker.withMiskDefaults
import misk.vitess.testing.TransactionIsolationLevel
import misk.vitess.testing.VitessTestDbStartupException

/** VitessDockerContainer validates user arguments and starts a Docker container with a vttestserver image. */
internal class VitessDockerContainer(
  private val containerName: String,
  private val debugStartup: Boolean,
  private val enableScatters: Boolean,
  private val keepAlive: Boolean,
  private val mysqlVersion: String,
  private val sqlMode: String,
  private val transactionIsolationLevel: TransactionIsolationLevel,
  private val transactionTimeoutSeconds: Duration,
  private val vitessClusterConfig: VitessClusterConfig,
  private val vitessImage: String,
  private val vitessSchemaManager: VitessSchemaManager,
  private val vitessVersion: Int,
) {
  private companion object {
    const val ENABLE_SCATTERS_ENV = "ENABLE_SCATTERS"
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

    const val DOCKER_HEALTH_CHECK_HOST = "localhost"
    const val DOCKER_START_RETRIES = 6
    const val DOCKER_START_RETRY_DELAY_MS = 5000L
    const val CONTAINER_START_RETRIES = 10
    const val CONTAINER_START_RETRY_DELAY_MS = 10000L
    const val CONTAINER_HEALTH_CHECK_INTERVAL_SECONDS = 5L
    const val CONTAINER_HEALTH_CHECK_RETRIES = 10
  }

  private val dockerClient: DockerClient = setupDockerClient()
  private val vitessMyCnf = VitessMyCnf(containerName, sqlMode, transactionIsolationLevel)

  fun start(): StartVitessContainerResult {
    validateVitessVersionArgs()
    startDockerIfNotRunning()

    val shouldCreateContainerResult = shouldCreateContainer(containerName)

    if (!shouldCreateContainerResult.newContainerNeeded) {
      return StartVitessContainerResult(
        newContainerNeeded = false,
        containerId = shouldCreateContainerResult.existingContainerId.toString(),
      )
    }

    println("Starting new container `$containerName`.")
    if (shouldCreateContainerResult.newContainerReason != null) {
      println("Reason for new container: ${shouldCreateContainerResult.newContainerReason}")
    }

    stopExistingContainers(containerName)

    val containerId = createContainer()
    startContainer(containerId)
    waitForContainerHealth(containerId)
    createDbaUserForSideCarDb(containerId)

    return StartVitessContainerResult(
      newContainerNeeded = true,
      newContainerReason = shouldCreateContainerResult.newContainerReason,
      containerId = containerId,
    )
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

  /**
   * This method checks if the container should be created based on the user arguments and the schema directory
   * contents.
   */
  private fun shouldCreateContainer(containerName: String): ShouldCreateVitessContainerResult {
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
        ENABLE_SCATTERS_ENV to ("$enableScatters" to "enableScatters"),
        KEYSPACES_ENV to (getKeyspacesString() to "keyspaces"),
        MYSQL_VERSION_ENV to (mysqlVersion to "mysqlVersion"),
        PORT_ENV to ("${vitessClusterConfig.basePort}" to "port"),
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

  private fun stopExistingContainers(containerName: String) {
    val existingContainer = findExistingContainer(containerName)

    existingContainer?.let { stopContainer(it) }

    // Also remove any containers that are using the same ports as the new container.
    vitessClusterConfig.allPorts().forEach { port ->
      val containers = dockerClient.listContainersCmd().withShowAll(true).withFilter("publish", listOf("$port")).exec()
      containers.forEach { container -> stopContainer(container) }
    }
  }

  private fun stopContainer(container: Container) {
    try {
      dockerClient.stopContainerCmd(container.id).withTimeout(5).exec()
    } catch (e: NotFoundException) {
      // If we are in this state, the container is already removed.
    } catch (e: NotModifiedException) {
      // If we are in this state, the container is already stopped.
    }
  }

  private fun removeExistingContainers(containerName: String) {
    val existingContainer = findExistingContainer(containerName)

    existingContainer?.let { removeContainer(it) }

    // Also remove any containers that are using the same ports as the new container.
    vitessClusterConfig.allPorts().forEach { port ->
      val containers = dockerClient.listContainersCmd().withShowAll(true).withFilter("publish", listOf("$port")).exec()
      containers.forEach { container -> removeContainer(container) }
    }
  }

  private fun removeContainer(container: Container) {
    try {
      dockerClient.removeContainerCmd(container.id).withForce(true).exec()
    } catch (e: NotFoundException) {
      // If we are in this state, the container is already removed.
    }
  }

  private fun startContainer(containerId: String) {
    try {
      dockerClient.startContainerCmd(containerId).exec()
    } catch (notModifiedException: NotModifiedException) {
      // The container is already started, ignore the exception.
    } catch (notFoundException: NotFoundException) {
      throw VitessTestDbStartupException("Container for `$containerId` was not found during start up.")
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
    dockerClient
      .logContainerCmd(containerId)
      .withStdOut(true)
      .withStdErr(true)
      .exec(logCallback)
      .awaitCompletion()
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
  private fun createContainer(): String {
    val images: List<Image> = dockerClient.listImagesCmd().withReferenceFilter(vitessImage).exec()
    if (images.isEmpty()) {
      println("Vitess image `$vitessImage` does not exist, proceeding to pull.")
      dockerClient.pullImageCmd(vitessImage).start().awaitCompletion()
    }

    val portBindings =
      vitessClusterConfig.allPorts().map { port -> PortBinding(Ports.Binding.bindPort(port), ExposedPort(port)) }
    val optionsFileDest = "$TEST_DB_DIR/my.cnf"
    val hostConfig =
      HostConfig()
        .withPortBindings(portBindings)
        .withBinds(Bind(vitessMyCnf.optionsFilePath.pathString, Volume(optionsFileDest)))
        .withAutoRemove(
          !debugStartup // If `debugStartup` is `true`, we keep the container running to inspect logs.
        ) // Otherwise, remove container when it stops.

    // The health check is run from the Docker daemon, so it needs to target localhost.
    val healthCheck =
      HealthCheck()
        .withTest(
          listOf(
            "CMD-SHELL",
            "mysql -h $DOCKER_HEALTH_CHECK_HOST --protocol=tcp -P ${vitessClusterConfig.vtgatePort} -u=${vitessClusterConfig.vtgateUser} --execute 'USE @primary;'",
          )
        )
        .withInterval(Duration.ofSeconds(CONTAINER_HEALTH_CHECK_INTERVAL_SECONDS).toNanos())
        .withRetries(CONTAINER_HEALTH_CHECK_RETRIES)

    val cmd =
      mutableListOf(
        "/vt/bin/vttestserver",
        "--alsologtostderr",
        "--port",
        "${vitessClusterConfig.basePort}",
        "--mysql_bind_host",
        "0.0.0.0",
        "--keyspaces",
        getKeyspacesString(),
        "--num_shards",
        getNumShardsString(),
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
      return runCreateContainerCmd(hostConfig, healthCheck, cmd)
    } catch (e: ConflictException) {
      // If we are in this state, a container already exists, possibly with older presets. We should
      // forcefully remove this container and try again.
      removeExistingContainers(containerName)
      return runCreateContainerCmd(hostConfig, healthCheck, cmd)
    }
  }

  private fun runCreateContainerCmd(
    hostConfig: HostConfig?,
    healthCheck: HealthCheck?,
    cmd: MutableList<String>,
  ): String {
    val container: CreateContainerResponse =
      dockerClient
        .createContainerCmd(vitessImage)
        .withName(containerName)
        .withHostConfig(hostConfig)
        .withPlatform("linux/amd64")
        .withHealthcheck(healthCheck)
        .withExposedPorts(*vitessClusterConfig.allPorts().map { ExposedPort(it) }.toTypedArray())
        .withEnv(
          "$ENABLE_SCATTERS_ENV=$enableScatters",
          "$KEEP_ALIVE_ENV=$keepAlive",
          "$KEYSPACES_ENV=${getKeyspacesString()}",
          "$MYSQL_VERSION_ENV=$mysqlVersion",
          "$PORT_ENV=${vitessClusterConfig.basePort}",
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

  private fun getKeyspacesString(): String = vitessSchemaManager.keyspaces.map { it.name }.sorted().joinToString(",")

  private fun getNumShardsString(): String =
    vitessSchemaManager.keyspaces.sortedBy { it.name }.joinToString(",") { it.shards.toString() }

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
          "CREATE USER 'vt_dba_tcp_full'@'%'; GRANT ALL ON *.* TO 'vt_dba_tcp_full'@'%';",
        )
        .exec()

    val logCallback = LogContainerResultCallback()
    dockerClient.execStartCmd(exec.id).exec(logCallback).awaitCompletion()

    val exitCode = dockerClient.inspectExecCmd(exec.id).exec().exitCodeLong
    if (exitCode != 0L) {
      throw VitessTestDbStartupException("Failed to create side car dba user: ${logCallback.getLogs()}")
    }
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
internal data class ShouldCreateVitessContainerResult(
  val newContainerNeeded: Boolean,
  val newContainerReason: String? = null,
  val existingContainerId: String? = null,
)

/**
 * Thia class contains information about the result of starting a Vitess Docker container. If the Docker container fails
 * to start, a [VitessTestDbStartupException] will be thrown instead.
 *
 * @property containerId The ID of the Docker container that was started.
 * @property newContainerNeeded Whether a new container was created or an existing one was reused.
 * @property newContainerReason The reason for creating a new container, if applicable.
 */
internal data class StartVitessContainerResult(
  val containerId: String,
  val newContainerNeeded: Boolean,
  val newContainerReason: String? = null,
)
