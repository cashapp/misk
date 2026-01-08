package wisp.config

import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.PrimitiveNode
import com.sksamuel.hoplite.StringNode
import com.sksamuel.hoplite.fp.valid
import com.sksamuel.hoplite.preprocessor.TraversingPrimitivePreprocessor
import java.io.FileNotFoundException
import wisp.resources.ClasspathResourceLoaderBackend
import wisp.resources.EnvironmentResourceLoaderBackend
import wisp.resources.FilesystemLoaderBackend
import wisp.resources.ResourceLoader

/**
 * Preprocessor for a config node. If the node is a StringNode and the value starts with the [prefix], then the node
 * value is treated as a resource location. This is loaded using the [resourceLoader] and returned as a StringNode.
 *
 * [prefix] is one of [ClasspathResourceLoaderBackend.SCHEME], [EnvironmentResourceLoaderBackend.SCHEME] or
 * [FilesystemLoaderBackend.SCHEME]
 */
class PrefixResourceLoaderPreprocessor
@JvmOverloads
constructor(val prefix: String, val resourceLoader: ResourceLoader = ResourceLoader.SYSTEM) :
  TraversingPrimitivePreprocessor() {

  init {
    require(
      prefix in
        listOf(
          ClasspathResourceLoaderBackend.SCHEME,
          FilesystemLoaderBackend.SCHEME,
          EnvironmentResourceLoaderBackend.SCHEME,
        )
    )
  }

  override fun handle(node: PrimitiveNode, context: DecoderContext): ConfigResult<Node> {
    return if (node is StringNode && node.value.startsWith(prefix)) {
      val nodeData = resourceLoader.utf8(node.value) ?: throw FileNotFoundException(node.value)
      StringNode(nodeData, node.pos, node.path).valid()
    } else {
      node.valid()
    }
  }
}
