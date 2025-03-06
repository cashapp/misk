package misk.docker

import com.github.dockerjava.core.DefaultDockerClientConfig
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import misk.docker.DockerCredentials.DEFAULT_DOCKER_REGISTRY_URL
import okio.FileSystem
import okio.Path.Companion.toPath

internal data class DockerConfig @JvmOverloads constructor(
  val credsStore: String? = null
)

data class DockerCredential(
  @Json(name = "Username")
  val username: String?,
  @Json(name = "Secret")
  val password: String?,
)

/**
 * A helper class for fetching docker credentials, necessary when pulling docker images.
 */
@OptIn(ExperimentalStdlibApi::class)
object DockerCredentials {
  private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
  private val dockerConfigAdapter = moshi.adapter<DockerConfig>()
  private val dockerCredentialAdapter = moshi.adapter<DockerCredential>()

  /**
   * If Docker is configured to use a credential store (`credsStore`), attempts to fetch a docker registry username
   * and password from the available credential store.
   *
   * Loads $HOME/.docker/config.json, determines whether a `credsStore` is defined.
   *
   * If `credsStore` is defined, uses it to fetch a Docker registry username and password.
   *
   * If no `credsStore` is defined, returns null. This SHOULD handle the case where Docker Desktop is
   * not running, e.g. in CI.
   * If no registry is defined, defaults to https://index.docker.io/v1/.
   */
  fun getDockerCredentials(
    registryUrl: String?,
    fs: FileSystem = FileSystem.SYSTEM
  ): DockerCredential? {
    val registry = registryUrl ?: DEFAULT_DOCKER_REGISTRY_URL

    return getDockerConfig(fs)?.credsStore?.let {
      fetchCredentials(it, registry)
    }
  }

  private fun fetchCredentials(credStore: String, registryUrl: String): DockerCredential? {
    val credentialCmd = "docker-credential-$credStore"

    return runCatching {
      val process = ProcessBuilder("sh", "-c", "echo $registryUrl | $credentialCmd get")
        .redirectErrorStream(true)
        .start()

      process.waitFor()

      val output = String(process.inputStream.readAllBytes())

      dockerCredentialAdapter.fromJson(output)
    }.getOrNull()
  }

  private fun getDockerConfig(fs: FileSystem): DockerConfig? =
    "${System.getProperty("user.home")}/.docker/config.json".toPath()
      .takeIf { fs.exists(it) }
      ?.let { path ->
        fs.read(path) { readUtf8() }
      }
      ?.let { dockerConfigAdapter.fromJson(it) }

  const val DEFAULT_DOCKER_REGISTRY_URL = "https://index.docker.io/v1/"
}

fun DefaultDockerClientConfig.Builder.withLocalDockerCredentials(registryUrl: String = DEFAULT_DOCKER_REGISTRY_URL) =
  apply {
    val credentials = DockerCredentials.getDockerCredentials(registryUrl)
    // Set the retrieved username and password
    withRegistryUsername(credentials?.username)
    withRegistryPassword(credentials?.password)
  }

fun DefaultDockerClientConfig.Builder.withMiskDefaults() = apply {
  withLocalDockerCredentials(DEFAULT_DOCKER_REGISTRY_URL)
}
