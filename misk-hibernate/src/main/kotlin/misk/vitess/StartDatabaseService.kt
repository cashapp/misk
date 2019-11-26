package misk.vitess

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.util.concurrent.AbstractIdleService
import com.squareup.moshi.Moshi
import misk.environment.Environment
import misk.environment.Environment.DEVELOPMENT
import misk.environment.Environment.TESTING
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.resources.ResourceLoader
import mu.KotlinLogging
import java.io.IOException
import java.util.Optional
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

fun runCommand(command: String): Int {
  StartDatabaseService.logger.info(command)
  return try {
    val process = ProcessBuilder("bash", "-c", command)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    process.waitFor(60, TimeUnit.MINUTES)
    return process.exitValue()
  } catch (e: IOException) {
    StartDatabaseService.logger.warn("'$command' threw exception", e)
    -1 // Failed
  }
}

/**
 * All Vitess clusters used by the app/test are tracked in a global cache as a [DockerVitessCluster].
 *
 * On startup, the service will look for a cluster in the cache, and if not found, look for it in
 * Docker by container name, or as a last resort start the container itself.
 *
 * On shutdown, the cache is invalidated by a JVM shutdown hook. On invalidation, the cache will
 * call the each entry's `stop()` method. If the cluster container was created in this JVM, it
 * will be stopped and removed. Otherwise (if the container was started by a different process), it
 * will be left running.
 */
class StartDatabaseService(
  qualifier: KClass<out Annotation>,
  private val environment: Environment,
  config: DataSourceConfig
) : AbstractIdleService() {
  var server: DatabaseServer? = null

  init {
    val name = qualifier.simpleName!!
    server = servers[CacheKey(name, config, environment)].orElse(null)

    // We need to do this outside of the service start up because this takes a really long time
    // the first time you do it and can cause service manager to time out.
    if (shouldStartServer()) {
      server?.pullImage()
    }
  }

  override fun startUp() {
    if (shouldStartServer()) {
      server?.start()
    }
  }

  private fun shouldStartServer() = environment == TESTING || environment == DEVELOPMENT

  override fun shutDown() {
  }

  data class CacheKey(
    val name: String,
    val config: DataSourceConfig,
    val environment: Environment
  )

  companion object {
    val logger = KotlinLogging.logger {}
    val docker: DockerClient = DockerClientBuilder.getInstance()
        .withDockerCmdExecFactory(NettyDockerCmdExecFactory())
        .build()
    val moshi = Moshi.Builder().build()

    /**
     * Global cache of running database servers.
     */
    val servers: LoadingCache<CacheKey, Optional<DatabaseServer>> = CacheBuilder.newBuilder()
        .removalListener<CacheKey, Optional<DatabaseServer>> { entry ->
          entry.value.ifPresent { it.stop() } }
        .build(CacheLoader.from { config: CacheKey? ->
          Optional.ofNullable(config?.let {
            createDatabaseServer(it)
          })
        })

    private fun createDatabaseServer(config: CacheKey): DatabaseServer? =
        when (config.config.type) {
          DataSourceType.VITESS_MYSQL, DataSourceType.VITESS -> {
            DockerVitessCluster(
                name = config.name,
                config = config.config,
                resourceLoader = ResourceLoader.SYSTEM,
                moshi = moshi,
                docker = docker)
          }
          DataSourceType.COCKROACHDB -> {
            DockerCockroachCluster(
                name = config.name,
                config = config.config,
                resourceLoader = ResourceLoader.SYSTEM,
                moshi = moshi,
                docker = docker)
          }
          else -> null
        }

    /**
     * Shut down the cached clusters on JVM exit.
     */
    init {
      Runtime.getRuntime().addShutdownHook(Thread {
        servers.invalidateAll()
      })
    }

    /**
     * A helper method to start the Vitess cluster outside of the dev server or test process, to
     * enable rapid iteration. This should be called directly a `main()` function, for example:
     *
     * MyAppVitessDaemon.kt:
     *
     *  fun main() {
     *    val config = MiskConfig.load<MyAppConfig>("myapp", Environment.TESTING)
     *    startVitessDaemon(MyAppDb::class, config.data_source_clusters.values.first().writer)
     *  }
     *
     */
    fun startVitessDaemon(
      /** The same qualifier passed into [HibernateModule], used to uniquely name the container */
      qualifier: KClass<out Annotation>,
      /** Config for the Vitess cluster */
      config: DataSourceConfig
    ) {
      val docker: DockerClient = DockerClientBuilder.getInstance()
          .withDockerCmdExecFactory(NettyDockerCmdExecFactory())
          .build()
      val moshi = Moshi.Builder().build()
      val dockerCluster =
          DockerVitessCluster(
              name = qualifier.simpleName!!,
              config = config,
              resourceLoader = ResourceLoader.SYSTEM,
              moshi = moshi,
              docker = docker)
      Runtime.getRuntime().addShutdownHook(Thread {
        dockerCluster.stop()
      })
      dockerCluster.start()
    }
  }
}
