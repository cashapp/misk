package wisp.resources

import okio.Buffer
import okio.BufferedSource

/**
 * A fake [ResourceLoader.Backend] loads resources from an in-memory map. This does not have the same well-formed
 * filepath guarantees that [FakeFilesystemLoaderBackend] provides, which assumes resource paths are file-like and will
 * throw exceptions for malformed resource paths
 */
@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  ReplaceWith(expression = "FakeResourceLoaderBackend", "misk.resources.FakeResourceLoaderBackend"),
)
class FakeResourceLoaderBackend(private val fakeResources: Map<String, String>) : ResourceLoader.Backend() {
  override fun checkPath(path: String) {
    require(fakeResources.containsKey(path))
  }

  override fun list(path: String): List<String> {
    return if (fakeResources.containsKey(path)) listOf(path) else emptyList()
  }

  override fun open(path: String): BufferedSource? {
    return fakeResources[path]?.let { Buffer().writeUtf8(it) }
  }

  override fun exists(path: String): Boolean {
    return fakeResources.containsKey(path)
  }
}
