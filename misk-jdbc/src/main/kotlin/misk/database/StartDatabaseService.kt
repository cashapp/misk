package misk.database

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.util.concurrent.AbstractIdleService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.resources.ResourceLoader
import mu.KotlinLogging
import wisp.deployment.Deployment
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
  private val deployment: Deployment,
  config: DataSourceConfig
) : AbstractIdleService() {
  var server: DatabaseServer? = null
  private var startupFailure: Throwable? = null

  init {
    if (shouldStartServer()) {
      val name = qualifier.simpleName!!
      server = servers[CacheKey(name, config, deployment)].orElse(null)

      // We need to do this outside of the service start up because this takes a really long time
      // the first time you do it and can cause service manager to time out.
      server?.pullImage()
    }
  }

  override fun startUp() {
    this.server?.start()
  }

  private fun shouldStartServer() = deployment.isTest || deployment.isLocalDevelopment

  override fun shutDown() {
  }

  data class CacheKey(
    val name: String,
    val config: DataSourceConfig,
    val deployment: Deployment
  )

  companion object {
    val logger = KotlinLogging.logger {}
    val docker: DockerClient = DockerClientBuilder.getInstance()
      .withDockerCmdExecFactory(NettyDockerCmdExecFactory())
      .build()
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()

    /**
     * Global cache of running database servers.
     */
    val servers: LoadingCache<CacheKey, Optional<DatabaseServer>> = CacheBuilder.newBuilder()
      .removalListener<CacheKey, Optional<DatabaseServer>> { entry ->
        entry.value.ifPresent { it.stop() }
      }
      .build(CacheLoader.from { config: CacheKey? ->
        Optional.ofNullable(config?.let {
          createDatabaseServer(it)
        })
      })

    private fun createDatabaseServer(config: CacheKey): DatabaseServer? =
      when (config.config.type) {
        DataSourceType.VITESS_MYSQL -> {
          DockerVitessCluster(
            name = config.name,
            config = config.config,
            resourceLoader = ResourceLoader.SYSTEM,
            moshi = moshi,
            docker = docker
          )
        }
        DataSourceType.COCKROACHDB -> {
          DockerCockroachCluster(
            name = config.name,
            config = config.config,
            resourceLoader = ResourceLoader.SYSTEM,
            moshi = moshi,
            docker = docker
          )
        }
        DataSourceType.TIDB -> {
          DockerTidbCluster(
            moshi = moshi,
            resourceLoader = ResourceLoader.SYSTEM,
            config = config.config,
            docker = docker
          )
        }
        DataSourceType.POSTGRESQL -> {
          DockerPostgresServer(
            config = config.config,
            docker = docker
          )
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
  }
}
