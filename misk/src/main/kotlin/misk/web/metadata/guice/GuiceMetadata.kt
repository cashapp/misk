package misk.web.metadata.guice

import com.google.inject.Binding
import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.Scope
import com.google.inject.spi.BindingScopingVisitor
import jakarta.inject.Inject
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import misk.web.metadata.toFormattedJson
import wisp.moshi.adapter
import wisp.moshi.defaultKotlinMoshi

// TODO consider moving to misk-admin so it can be internal scoped
data class GuiceMetadata(
  val guice: GuiceMetadataProvider.Metadata
) : Metadata(
  metadata = guice,
  prettyPrint = "Total Bindings: ${guice.bindingMetadata.size}\n\n" + defaultKotlinMoshi
    .adapter<Set<GuiceMetadataProvider.BindingMetadata>>()
    .toFormattedJson(guice.bindingMetadata),
  descriptionString = "Direct injection bindings, powered by Guice. This metadata is work-in-progress."
)

class GuiceMetadataProvider @Inject constructor() : MetadataProvider<GuiceMetadata> {
  @Inject lateinit var injector: Injector

  private val keyAnnotationRegex = """annotation=([^]]+)""".toRegex()

  override val id = "guice"

  data class Metadata(
    val bindingMetadata: Set<BindingMetadata>
  )

  data class BindingMetadata(
    val type: String,
    val source: String,
    val scope: String?,
    val provider: String,
    val annotation: String?
  ) {
    override fun toString(): String {
      return "$type(source=$source, scope=$scope, provider=$provider, annotation=$annotation)"
    }
  }

  val allBindings by lazy {
    injector.allBindings
  }

  // TODO should this cache the result?
  override fun get(): GuiceMetadata {
    val bindingMetadataSet = allBindings.map { it.value.toMetadata() }.toSet()

    return GuiceMetadata(Metadata(bindingMetadata = bindingMetadataSet))
  }

  private fun String.stripCommonPackages(): String {
    var pretty = this

    val commonPackages = listOf("com.google.inject", "jakarta.inject", "javax.inject", "java.util", "java.lang")
    commonPackages.forEach {
      pretty = pretty.replace("$it.", "")
    }

    return pretty
  }

  private fun Binding<*>.toMetadata() : BindingMetadata {
    val key = key
    val type = key.typeLiteral.toString().stripCommonPackages()
    val source = source.toString()
    val scope = acceptScopingVisitor(ScopeVisitor())
    //TODO improve the provider string
    val provider = provider?.toString() ?: ""
    val annotation = key.prettyPrintAnnotation()
    return BindingMetadata(type, source, scope, provider, annotation)
  }

  private class ScopeVisitor : BindingScopingVisitor<String?> {
    override fun visitEagerSingleton(): String {
      return "Singleton"
    }

    override fun visitScope(scope: Scope): String {
      return scope.toString()
    }

    override fun visitNoScoping(): String? {
      return null
    }

    override fun visitScopeAnnotation(scopeAnnotation: Class<out Annotation>?): String {
      return "Annotation: $scopeAnnotation"
    }
  }

  private fun Key<*>.prettyPrintAnnotation(): String {
    return annotation?.prettyPrint() ?: maybeExtractAnnotationFromKey(toString())
  }

  private fun maybeExtractAnnotationFromKey(keyToString: String): String {
    val matchResult = keyAnnotationRegex.find(keyToString)
    val annotation = matchResult?.groupValues?.get(1) ?: ""
    return if (annotation == "[none") {
      ""
    } else {
      annotation
    }
  }

  private fun Annotation.prettyPrint(): String {
    val annotationString = toString()
    return if (annotationClass.qualifiedName == "com.google.inject.internal.Element") {
      if (annotationString.contains("type=MULTIBINDER")) {
        "Multibinder element"
      } else if (annotationString.contains("type=MAPBINDER")) {
        var keyType = annotationString.split("keyType=").last().removeSuffix(")")
        keyType = keyType.stripCommonPackages()
        "Mapbinder<Key=$keyType>"
      } else {
        annotationString
      }
    } else {
      annotationString
    }
  }
}
