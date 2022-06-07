package wisp.resources

import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import java.util.TreeMap

/**
 * Read-write resources stored only in memory. Most useful for testing. It is possible to have
 * multiple instances of this backend.
 *
 * This uses the scheme `memory:`.
 */
class MemoryResourceLoaderBackend : ResourceLoader.Backend() {
  private val resources = TreeMap<String, ByteString>()
  private var resourceChangedListeners = mutableMapOf<String, (address: String) -> Unit>()

  override fun open(path: String): BufferedSource? {
    val resource = resources[path] ?: return null
    return Buffer().write(resource)
  }

  override fun put(path: String, data: ByteString) {
    resources[path] = data
    resourceChanged(SCHEME, path)
  }

  override fun exists(path: String) = resources[path] != null

  override fun all(): Set<String> = resources.keys

  override fun watch(path: String, resourceChangedListener: (address: String) -> Unit) {
    resourceChangedListeners[path] = resourceChangedListener
  }

  override fun unwatch(path: String) {
    resourceChangedListeners.remove(path)
  }

  fun resourceChanged(scheme: String, path: String) {
    val resourceChangedListener = resourceChangedListeners[path]
    if (resourceChangedListener != null) {
      resourceChangedListener(scheme + path)
    }
  }

  companion object {
    const val SCHEME = "memory:"
  }
}
