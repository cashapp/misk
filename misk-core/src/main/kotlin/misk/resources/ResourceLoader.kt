package misk.resources

import okio.BufferedSource
import okio.ByteString
import wisp.resources.ClasspathResourceLoaderBackend
import wisp.resources.FilesystemLoaderBackend
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Singleton
import wisp.resources.ResourceLoader as WispResourceLoader

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
@Deprecated("Use from misk-config instead")
@Singleton
class ResourceLoader @Inject constructor(
  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
  backends: java.util.Map<String, WispResourceLoader.Backend>
) {
  @Suppress("UNCHECKED_CAST")
  val delegate =
    WispResourceLoader(backends as Map<String, wisp.resources.ResourceLoader.Backend>)

  /** Return a buffered source for `address`, or null if no such resource exists. */
  fun open(address: String): BufferedSource? = delegate.open(address)

  /** Writes a resource as UTF-8. Throws if the backend is readonly. */
  fun put(address: String, utf8: String) = delegate.put(address, utf8)

  /** Writes a resource. Throws if the backend is readonly. */
  fun put(address: String, data: ByteString) = delegate.put(address, data)

  /** Returns true if a resource at `address` exists. */
  fun exists(address: String): Boolean = delegate.exists(address)

  /** Returns the full path of the resources that are immediate children of `address`. */
  fun list(address: String): List<String> = delegate.list(address)

  fun walk(address: String): List<String> = delegate.walk(address)

  /**
   * Return the contents of `address` as a string, or null if no such resource exists. Note that
   * this method decodes the resource on every use. It is the caller's responsibility to cache the
   * result if it is to be loaded frequently.
   */
  fun utf8(address: String): String? = delegate.utf8(address)

  /**
   * Like [utf8], but throws [IllegalStateException] if the resource is missing.
   */
  fun requireUtf8(address: String): String = delegate.requireUtf8(address)

  /**
   * Copies all resources with [root] as a prefix to the directory [dir].
   */
  fun copyTo(root: String, dir: Path) = delegate.copyTo(root, dir)

  @Deprecated(
    "Use wisp.resources.ResourceLoader.Backend directly",
    replaceWith = ReplaceWith("Backend", "wisp.resources.ResourceLoader.Backend")
  )
  abstract class Backend : WispResourceLoader.Backend()

  companion object {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
    val SYSTEM = ResourceLoader(
      mapOf(
        "classpath:" to ClasspathResourceLoaderBackend,
        "filesystem:" to FilesystemLoaderBackend
      ) as java.util.Map<String, WispResourceLoader.Backend>
    )
  }
}
