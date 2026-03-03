package misk.testing

import com.google.inject.Provides
import misk.inject.KAbstractModule
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Singleton

/** A temporary folder for use by a given test */
class TemporaryFolder(val root: Path) {
  /** Deletes all files and folders under the temporary folder */
  fun delete() {
    root.toFile().deleteRecursively()
  }

  /** @return a new folder with a randomly generated name */
  fun newFolder(): Path = Files.createTempDirectory(root, "")

  /** @return a new folder with the given name */
  fun newFolder(name: String): Path = Files.createDirectories(root.resolve(name))

  /** @return a new file with a randomly generated name */
  fun newFile(): Path = Files.createTempFile(root, "", "")

  /** @return a new file with the given name */
  fun newFile(name: String): Path = Files.createFile(root.resolve(name))
}

class TemporaryFolderModule : KAbstractModule() {
  override fun configure() {
    multibind<AfterEachCallback>().to<DeleteTempFolder>()
  }

  @Provides
  @Singleton
  fun provideTemporaryFolder(): TemporaryFolder {
    val tempDir = Files.createTempDirectory("test-")
    tempDir.toFile().deleteOnExit()

    return TemporaryFolder(tempDir)
  }

  class DeleteTempFolder @Inject constructor(
    private val tempDir: TemporaryFolder
  ) : AfterEachCallback {
    override fun afterEach(context: ExtensionContext) {
      tempDir.delete()
    }
  }
}
