package wisp.resources

import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.buffer
import okio.sink
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * ResourceLoader is a testable API for loading resources from the classpath, from the filesystem,
 * from memory, or from another [Backend] source.
 *
 * Resource addresses have a scheme name, a colon, and an absolute filesystem-like path:
 * `classpath:/migrations/v1.sql`. Schemes identify backends `classpath:` or `memory:`. Paths start
 * with a slash and have any number of segments.
 *
 * **Classpath resources** use the scheme `classpath:`. The backend reads data from the
 * `src/main/resources` of the project's modules and the contents of all library `.jar` files.
 * Classpath resources are read-only.
 *
 * **Filesystem resources** use the scheme `filesystem:`. The backend reads data from the host
 * machine's local filesystem. It is read-only and does not support [list].
 *
 * **Memory resources** use the scheme `memory:`. The backend starts empty and is populated by calls
 * to [put].
 *
 * Other backends are permitted. They should be registered with a `MapBinder` with the backend
 * scheme like `classpath:` as the key.
 */
@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  ReplaceWith(expression = "ResourceLoader","misk.resources.ResourceLoader")
)
open class ResourceLoader(
    private val backends: Map<String, Backend>
) {
    init {
        for (prefix in backends.keys) {
            require(prefix.matches(Regex("[^/:]+:")))
        }
    }

    /** Return a buffered source for `address`, or null if no such resource exists. */
    fun open(address: String): BufferedSource? {
        checkAddress(address)

        val (scheme, path) = parseAddress(address)
        val backend = backends[scheme] ?: return null
        backend.checkPath(path)
        return backend.open(path)
    }

    /** Writes a resource as UTF-8. Throws if the backend is readonly. */
    fun put(address: String, utf8: String) {
        put(address, utf8.encodeUtf8())
    }

    /** Writes a resource. Throws if the backend is readonly. */
    fun put(address: String, data: ByteString) {
        checkAddress(address)

        val (scheme, path) = parseAddress(address)
        val backend = backends[scheme] ?: return
        backend.checkPath(path)
        backend.put(path, data)
    }

    /** Returns true if a resource at `address` exists. */
    fun exists(address: String): Boolean {
        try {
            checkAddress(address)
        } catch (e: IllegalArgumentException) {
            return false
        }

        val (scheme, path) = parseAddress(address)
        val backend = backends[scheme] ?: return false
        try {
          backend.checkPath(path)
        } catch (e: IllegalArgumentException) {
          return false
        }
        return backend.exists(path)
    }

    /** Returns the full path of the resources that are immediate children of `address`. */
    fun list(address: String): List<String> {
        try {
            checkAddress(address)
        } catch (e: IllegalArgumentException) {
            return listOf()
        }

        val (scheme, path) = parseAddress(address)
        val backend = backends[scheme] ?: return listOf()
        try {
          backend.checkPath(path)
        } catch (e: IllegalArgumentException) {
          return listOf()
        }
        return backend.list(path).map { scheme + it }
    }

    fun walk(address: String): List<String> {
        val resourcesResult = mutableListOf<String>()
        for (resource in list(address)) {
            if (list(resource).isEmpty()) {
                resourcesResult.add(resource)
            } else {
                resourcesResult.addAll(walk(resource))
            }
        }
        return Collections.unmodifiableList(resourcesResult)
    }

    /**
     * Return the contents of `address` as a string, or null if no such resource exists. Note that
     * this method decodes the resource on every use. It is the caller's responsibility to cache the
     * result if it is to be loaded frequently.
     */
    fun utf8(address: String): String? {
        val source = open(address) ?: return null
        return source.use { it.readUtf8() }
    }

    /**
     * Like [utf8], but throws [IllegalStateException] if the resource is missing.
     */
    fun requireUtf8(address: String): String {
        return utf8(address) ?: error("could not load resource $address")
    }

    /**
     * Return the contents of `address` as bytes, or null if no such resource exists. Note that
     * this method reads the resource on every use. It is the caller's responsibility to cache the
     * result if it is to be loaded frequently.
     */
    fun bytes(address: String): ByteString? {
      val source = open(address) ?: return null
      return source.use { it.readByteString() }
    }

    /**
     * Like [bytes], but throws [IllegalStateException] if the resource is missing.
     */
    fun requireBytes(address: String): ByteString {
      return bytes(address) ?: error("could not load resource $address")
    }

    private fun checkAddress(address: String) {
        require(address.matches(Regex("([^/:]+:).+"))) { "unexpected address $address" }
    }

    /**
     * Decodes an address like `classpath:/migrations/v1.sql` into a backend scheme like `classpath:`
     * and a backend-specific path like `/migrations/v1.sql`.
     */
    private fun parseAddress(path: String): Address {
        val colon = path.indexOf(':')
        check(colon != -1) { "address scheme not specified in: $path" }
        return Address(path.substring(0, colon + 1), path.substring(colon + 1))
    }

    /**
     * Copies all resources with [root] as a prefix to the directory [dir].
     */
    fun copyTo(root: String, dir: Path) {
        val prefix = if (root.endsWith("/")) root else "$root/"
        for (resource in walk(root)) {
            val destination = dir.resolve(resource.substring(prefix.length))
            copyResource(resource, destination)
        }
    }

    /**
     * Copy the resource to the specified filename [destination], creating all of the parent
     * directories if necessary.
     */
    private fun copyResource(address: String, destination: Path) {
        Files.createDirectories(destination.parent)
        open(address).use { i ->
            destination.sink().buffer().use { o ->
                o.writeAll(i!!)
            }
        }
    }

    fun watch(address: String, resourceChangedListener: (address: String) -> Unit) {
        checkAddress(address)

        val (scheme, path) = parseAddress(address)
        val backend = backends[scheme] ?: return
        backend.checkPath(path)
        backend.watch(path, resourceChangedListener)
    }

    fun unwatch(address: String) {
        checkAddress(address)

        val (scheme, path) = parseAddress(address)
        val backend = backends[scheme] ?: return
        backend.checkPath(path)
        backend.unwatch(path)
    }

    private data class Address(val scheme: String, val path: String)

    abstract class Backend {

        abstract fun open(path: String): BufferedSource?

        abstract fun exists(path: String): Boolean

        open fun put(path: String, data: ByteString) {
            throw UnsupportedOperationException("cannot put $path; ${this::class} is readonly")
        }

        open fun all(): Set<String> {
            throw UnsupportedOperationException("${this::class} doesn't support all()")
        }

        open fun list(path: String): List<String> {
            val prefix = if (path.endsWith("/")) path else "$path/"
            val result = mutableSetOf<String>()
            for (key in all()) {
                if (!key.startsWith(prefix)) continue
                val slash = key.indexOf('/', prefix.length)
                if (slash == -1) {
                    result.add(key)
                } else {
                    result.add(key.substring(0, slash))
                }
            }
            return Collections.unmodifiableList(result.toList())
        }

        open fun watch(path: String, resourceChangedListener: (address: String) -> Unit) {
            throw UnsupportedOperationException("${this::class} doesn't support watch")
        }

        open fun unwatch(path: String) {
            throw UnsupportedOperationException("${this::class} doesn't support unwatch")
        }

        /*
         * By default, backends will assume a file-like address path (i.e. path/to/file.txt) unless
         * a different implementation is provided in the backend.
         */
        open fun checkPath(path: String) {
          require(path.matches(Regex("(/[^/]+)+/?"))) { "unexpected address $path" }
        }
    }

    companion object {
        val SYSTEM = ResourceLoader(
            mapOf(
                ClasspathResourceLoaderBackend.SCHEME to ClasspathResourceLoaderBackend,
                FilesystemLoaderBackend.SCHEME to FilesystemLoaderBackend,
                EnvironmentResourceLoaderBackend.SCHEME to EnvironmentResourceLoaderBackend,
            )
        )
    }
}
