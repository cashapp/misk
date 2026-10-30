package misk.config

import java.util.Collections
import java.util.IdentityHashMap
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.lowlevel.Present
import org.snakeyaml.engine.v2.api.lowlevel.Serialize
import org.snakeyaml.engine.v2.composer.Composer
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.NodeTuple
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode
import org.snakeyaml.engine.v2.nodes.Tag
import org.snakeyaml.engine.v2.parser.ParserImpl
import org.snakeyaml.engine.v2.resolver.ScalarResolver
import org.snakeyaml.engine.v2.scanner.StreamReader
import org.snakeyaml.engine.v2.schema.JsonSchema

/** Resolves document-local references without constructing JVM values or resolving resources. */
internal object YamlReferences {
  private val schema =
    object : JsonSchema() {
      override fun getScalarResolver(): ScalarResolver {
        val delegate = super.getScalarResolver()
        return ScalarResolver { value, implicit ->
          if (implicit && value == "<<") Tag.MERGE else delegate.resolve(value, implicit)
        }
      }
    }
  private val loadSettings = LoadSettings.builder().setSchema(schema).build()
  private val dumpSettings = DumpSettings.builder().build()

  fun resolve(yaml: String): String {
    val composer =
      object : Composer(loadSettings, ParserImpl(loadSettings, StreamReader(loadSettings, yaml))) {
        override fun remove(): Unit = throw UnsupportedOperationException()

        override fun composeMappingChildren(children: MutableList<NodeTuple>, node: MappingNode) {
          // Defer merges until expansion, where cycles and expanded size can be checked.
          children.add(NodeTuple(composeKeyNode(node), composeValueNode(node)))
        }
      }
    val root = composer.singleNode.orElse(null) ?: return yaml
    val expanded = Expansion().expand(root)
    return Present(dumpSettings).emitToString(Serialize(dumpSettings).serializeOne(expanded).iterator())
  }

  private class Expansion {
    private val active = Collections.newSetFromMap(IdentityHashMap<Node, Boolean>())
    private var nodes = 0
    private var characters = 0L

    fun expand(node: Node): Node {
      require(active.size < 100) { "YAML reference nesting exceeds 100 levels" }
      require(++nodes <= 100_000) { "Expanded YAML exceeds 100000 nodes" }
      require(active.add(node)) { "Cyclic YAML reference" }
      try {
        return when (node) {
          is ScalarNode -> {
            characters += node.value.length
            require(characters <= 10_000_000) { "Expanded YAML exceeds 10000000 characters" }
            ScalarNode(node.tag, node.value, node.scalarStyle)
          }
          is SequenceNode -> SequenceNode(node.tag, node.value.map(::expand), node.flowStyle)
          is MappingNode -> expandMapping(node)
          else -> error("Unsupported YAML node: ${node.nodeType}")
        }
      } finally {
        active.remove(node)
      }
    }

    private fun expandMapping(node: MappingNode): MappingNode {
      val explicit = mutableListOf<NodeTuple>()
      val inherited = linkedMapOf<String, NodeTuple>()
      for (tuple in node.value) {
        val key = tuple.keyNode
        require(key is ScalarNode) { "YAML configuration keys must be scalars" }
        if (key.tag == Tag.MERGE) {
          val source = expand(tuple.valueNode)
          val sources = if (source is SequenceNode) source.value else listOf(source)
          for (mapping in sources) {
            require(mapping is MappingNode) { "YAML merge source must be a mapping or a sequence of mappings" }
            for (entry in mapping.value) {
              // In a merge sequence the first mapping wins, unlike cross-file overrides.
              inherited.putIfAbsent((entry.keyNode as ScalarNode).value, entry)
            }
          }
        } else {
          explicit.add(NodeTuple(expand(key), expand(tuple.valueNode)))
        }
      }
      val explicitKeys = explicit.map { (it.keyNode as ScalarNode).value }.toSet()
      val entries = inherited.filterKeys { it !in explicitKeys }.values + explicit
      return MappingNode(node.tag, entries.toList(), node.flowStyle)
    }
  }
}
