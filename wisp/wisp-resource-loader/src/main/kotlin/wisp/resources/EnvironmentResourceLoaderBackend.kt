package wisp.resources

import okio.Buffer
import okio.BufferedSource

/**
 * Read-only resources that are fetched from environment variables.
 *
 * This uses the scheme `environment:`.
 */
object EnvironmentResourceLoaderBackend : ResourceLoader.Backend() {

    const val SCHEME = "environment:"

    override fun list(path: String): List<String> {
        require(path.isNotBlank())

        return listOf(normalisedPath(path))
    }

    override fun open(path: String): BufferedSource? {
      val value = System.getenv(normalisedPath(path)) ?: return null

      val buffer = Buffer()
      buffer.writeUtf8(value)
      return buffer
    }

    override fun exists(path: String): Boolean {
      return System.getenv(normalisedPath(path)) != null
    }

    override fun checkPath(path: String) {
      require(path.isNotBlank()) { "unexpected path $path" }
    }

    private fun normalisedPath(path: String) = path.trim()
}
