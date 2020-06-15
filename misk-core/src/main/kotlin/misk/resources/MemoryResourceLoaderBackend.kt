package misk.resources

import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import java.util.TreeMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-write resources stored only in memory. Most useful for testing. It is possible to have
 * multiple instances of this backend.
 *
 * This uses the scheme `memory:`.
 */
@Singleton
internal class MemoryResourceLoaderBackend @Inject constructor() : ResourceLoader.Backend() {
  private val resources = TreeMap<String, ByteString>()

  override fun open(path: String): BufferedSource? {
    val resource = resources[path] ?: return null
    return Buffer().write(resource)
  }

  override fun put(path: String, data: ByteString) {
    resources[path] = data
  }

  override fun exists(path: String) = resources[path] != null

  override fun all(): Set<String> = resources.keys
}
