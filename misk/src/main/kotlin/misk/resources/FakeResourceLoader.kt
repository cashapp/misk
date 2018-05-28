package misk.resources

import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import java.util.TreeMap
import javax.inject.Singleton

/**
 * An in-memory resource loader.
 */
@Singleton
class FakeResourceLoader : ResourceLoader() {
  private val resources = TreeMap<String, ByteString>()

  fun put(path: String, utf8: String) {
    put(path, ByteString.encodeUtf8(utf8))
  }

  fun put(path: String, data: ByteString) {
    resources[path] = data
  }

  override fun all() = resources.keys

  override fun open(path: String): BufferedSource? {
    val resource = resources[path] ?: return null
    return Buffer().write(resource)
  }

  override fun utf8(path: String) = resources[path]?.utf8()
}