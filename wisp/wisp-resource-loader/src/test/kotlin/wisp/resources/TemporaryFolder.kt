package wisp.resources

import java.nio.file.Files
import java.nio.file.Path

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
