package wisp.config

import com.sksamuel.hoplite.preprocessor.PrefixProcessor
import java.io.FileNotFoundException
import wisp.resources.ResourceLoader

/**
 * A preprocessor that loads the content of the node from the specified classpath location
 * using the supplied resource loader.
 */
class ClasspathResourceLoaderPreprocessor(
  val resourceLoader: ResourceLoader = ResourceLoader.SYSTEM
) : PrefixProcessor("classpath:") {

  override fun processString(valueWithoutPrefix: String): String {
    val address = "classpath:$valueWithoutPrefix"
    return resourceLoader.utf8(address) ?: throw FileNotFoundException(address)
  }

}
