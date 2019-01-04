package misk.resources

import okio.Buffer
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * A fake [FilesystemLoaderBackend] that loads file contents from an in-memory map. The map
 * can be populated by adding to the [ForFakeFiles] map.
 *
 * ```
 * newMapBinder<String, String>(ForFakeFiles::class).addBinding("/etc/foo.txt").toInstance("hello!")
 * ```
 */
@Singleton
internal class FakeFilesystemLoaderBackend @Inject constructor(
  @ForFakeFiles private val files: Map<String, String>
) : ResourceLoader.Backend() {
  override fun open(path: String): BufferedSource? {
    val file = files[path] ?: return null
    return Buffer().write(file.encodeUtf8())
  }

  override fun exists(path: String) = files.containsKey(path)
}

@Qualifier
annotation class ForFakeFiles
