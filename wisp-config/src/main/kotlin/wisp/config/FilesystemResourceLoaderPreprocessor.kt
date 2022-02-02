package wisp.config

import com.sksamuel.hoplite.preprocessor.PrefixProcessor
import java.io.FileNotFoundException
import wisp.resources.ResourceLoader

/**
 * A preprocessor that loads the content of the node from the specified filesystem location
 * using the supplied resource loader.
 */
class FilesystemResourceLoaderPreprocessor(
  val resourceLoader: ResourceLoader = ResourceLoader.SYSTEM
) : PrefixProcessor("filesystem:") {

  override fun processString(valueWithoutPrefix: String): String {
    val address = "filesystem:$valueWithoutPrefix"
    return resourceLoader.utf8(address) ?: throw FileNotFoundException(address)
  }

}
