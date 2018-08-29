package misk.resources

import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Singleton

/**
 * Read-only resources that are fetched from the local filesystem using absolute paths.
 *
 * This uses the scheme `filesystem:`.
 */
@Singleton
internal object FilesystemLoaderBackend : ResourceLoader.Backend() {
  override fun open(path: String): BufferedSource? {
    val file = File(path)
    try {
      return file.source().buffer()
    } catch (e: FileNotFoundException) {
      return null
    }
  }

  override fun exists(path: String) = File(path).exists()
}
