package wisp.config

import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.PrimitiveNode
import com.sksamuel.hoplite.StringNode
import com.sksamuel.hoplite.fp.valid
import com.sksamuel.hoplite.preprocessor.TraversingPrimitivePreprocessor
import wisp.resources.ResourceLoader
import java.io.FileNotFoundException

/**
 * Preprocessor for a config node.  If the node is a StringNode and the value starts with the
 * [prefix], then the node value is treated as a resource location.  This is loaded using the
 * [resourceLoader] and returned as a StringNode.
 *
 * [prefix] is either [CLASSPATH_PREFIX] or [FILESYSTEM_PREFIX]
 */
class PrefixResourceLoaderPreprocessor(
  val prefix : String,
  val resourceLoader: ResourceLoader = ResourceLoader.SYSTEM
) : TraversingPrimitivePreprocessor() {

  init {
    require(prefix in listOf(CLASSPATH_PREFIX, FILESYSTEM_PREFIX))
  }

  override fun handle(node: PrimitiveNode): ConfigResult<Node> {
    return if (node is StringNode && node.value.startsWith(prefix)) {
      val nodeData = resourceLoader.utf8(node.value) ?: throw FileNotFoundException(node.value)
      StringNode(nodeData, node.pos, node.path).valid()
    } else {
      node.valid()
    }
  }

  companion object {
    const val CLASSPATH_PREFIX = "classpath:"
    const val FILESYSTEM_PREFIX = "filesystem:"
  }
}
