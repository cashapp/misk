package misk.web.metadata.guice

import com.google.inject.Binding
import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.Scope
import com.google.inject.multibindings.MapBinderBinding
import com.google.inject.multibindings.MultibinderBinding
import com.google.inject.multibindings.MultibindingsTargetVisitor
import com.google.inject.multibindings.OptionalBinderBinding
import com.google.inject.spi.BindingScopingVisitor
import com.google.inject.spi.ConstructorBinding
import com.google.inject.spi.ConvertedConstantBinding
import com.google.inject.spi.ExposedBinding
import com.google.inject.spi.InstanceBinding
import com.google.inject.spi.LinkedKeyBinding
import com.google.inject.spi.ProviderBinding
import com.google.inject.spi.ProviderInstanceBinding
import com.google.inject.spi.ProviderKeyBinding
import com.google.inject.spi.UntargettedBinding
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

  override val id = "guice"

  data class Metadata(
    val bindingMetadata: Set<BindingMetadata>,
  )

  data class BindingMetadata(
    val type: String,
    val typePackage: String,
    val source: String,
    val scope: String?,
    val provider: String,
    val annotation: String?,
    val subElements: List<BindingMetadata>?,
    val subElementsType: String?,
  ) {
    override fun toString(): String {
      return "$type(source=$source, scope=$scope, provider=$provider, annotation=$annotation, subElements=$subElements)"
    }
  }

  val allBindings by lazy {
    injector.allBindings
  }

  // TODO should this cache the result?
  override fun get(): GuiceMetadata {
    val allBindingMetadataSet = allBindings.mapNotNull { it.value.toMetadata() }.toSet()
    val multibindingTypes = allBindingMetadataSet.mapNotNull { it.subElementsType }.flatMap { type ->
      listOf("List<? extends $type>", "List<$type>", "Set<? extends $type>", "Set<$type>", "Collection<Provider<$type>>")
    }.toSet()
    val bindingMetadataSet = allBindingMetadataSet.filter {bindingMetadata ->
      bindingMetadata.subElementsType != null || !multibindingTypes.contains(bindingMetadata.type)
    }.toSet()

    return GuiceMetadata(Metadata(bindingMetadata = bindingMetadataSet))
  }

  private fun Binding<*>.toMetadata() : BindingMetadata? {
    return acceptTargetVisitor(MetadataBuilderVisitor())
  }

  private class MetadataBuilderVisitor : MultibindingsTargetVisitor<Any?,BindingMetadata?> {
    private val keyAnnotationRegex = """annotation=([^]]+)""".toRegex()
    override fun visit(multibinding: MultibinderBinding<out Any>?): BindingMetadata? {
      return if (multibinding?.elements?.isNotEmpty() == true) {
        val sample = multibinding.elements.first().toMetadata()
        val subElements = multibinding.elements.mapNotNull { it.toMetadata() }
        BindingMetadata(
          type = "Multibinder<${sample.type}>",
          typePackage = sample.typePackage,
          source = "",
          scope = null,
          provider = "",
          annotation = null,
          subElements = subElements,
          subElementsType = sample.type,
        )
      } else {
        null
      }
    }

    override fun visit(mapbinding: MapBinderBinding<out Any>?): BindingMetadata? {
      return if (mapbinding?.entries?.isNotEmpty() == true) {
        val sample = mapbinding.entries.first().value.toMetadata()
        val type = "Mapbinder<Key=${mapbinding.keyTypeLiteral.toString().stripCommonPackages()}, Value=${sample.type}>"
        val subElements = mapbinding.entries.mapNotNull { it.value.toMetadata() }
        BindingMetadata(
          type = type,
          typePackage = sample.typePackage,
          source = "",
          scope = null,
          provider = "",
          annotation = null,
          subElements = subElements,
          subElementsType = sample.type,
        )
      } else {
        null
      }
    }

    override fun visit(optionalbinding: OptionalBinderBinding<out Any>?): BindingMetadata? {
      return optionalbinding?.actualBinding?.let { singleBindingMetadataSkipSubElements(optionalbinding.actualBinding) } ?:
        optionalbinding?.defaultBinding?.let { singleBindingMetadataSkipSubElements(optionalbinding.defaultBinding) }
    }

    override fun visit(binding: InstanceBinding<out Any>?): BindingMetadata? {
      return binding?.let { singleBindingMetadataSkipSubElements(binding) }
    }

    override fun visit(binding: ProviderInstanceBinding<out Any>?): BindingMetadata? {
      return binding?.let { singleBindingMetadataSkipSubElements(binding) }
    }

    override fun visit(binding: ProviderKeyBinding<out Any>?): BindingMetadata? {
      return binding?.let { singleBindingMetadataSkipSubElements(binding) }
    }

    override fun visit(binding: LinkedKeyBinding<out Any>?): BindingMetadata? {
      return binding?.let { singleBindingMetadataSkipSubElements(binding) }
    }

    override fun visit(binding: ExposedBinding<out Any>?): BindingMetadata? {
      return binding?.let { singleBindingMetadataSkipSubElements(binding) }
    }

    override fun visit(binding: UntargettedBinding<out Any>?): BindingMetadata? {
      return binding?.let { singleBindingMetadataSkipSubElements(binding) }
    }

    override fun visit(binding: ConstructorBinding<out Any>?): BindingMetadata? {
      return binding?.let { singleBindingMetadataSkipSubElements(binding) }
    }

    override fun visit(binding: ConvertedConstantBinding<out Any>?): BindingMetadata? {
      return binding?.let { singleBindingMetadataSkipSubElements(binding) }
    }

    override fun visit(binding: ProviderBinding<*>?): BindingMetadata? {
      return binding?.let { singleBindingMetadataSkipSubElements(binding) }
    }

    private fun singleBindingMetadataSkipSubElements(binding: Binding<*>) : BindingMetadata?{
      return if (isSubElement(binding)) {
        null
      } else {
        binding.toMetadata()
      }
    }

    private fun isSubElement(binding: Binding<*>) : Boolean {
      return binding.key.annotation?.annotationClass?.qualifiedName == "com.google.inject.internal.Element"
    }

    private fun Binding<*>.toMetadata(): BindingMetadata {
      val key = key
      val type = key.typeLiteral.toString().stripCommonPackages()
      val source = source.toString()
      val scope = acceptScopingVisitor(ScopeVisitor())
      val provider = provider?.toString() ?: ""
      val annotation = key.prettyPrintAnnotation()
      return BindingMetadata(
        type = type,
        typePackage = key.typeLiteral.rawType.packageName,
        source = source,
        scope = scope,
        provider = provider,
        annotation = annotation,
        subElements = null,
        subElementsType = null,
      )
    }

    private fun String.stripCommonPackages(): String {
      var pretty = this

      val commonPackages = listOf("com.google.inject", "jakarta.inject", "javax.inject", "java.util", "java.lang")
      commonPackages.forEach {
        pretty = pretty.replace("$it.", "")
      }

      return pretty
    }

    private fun Annotation.prettyPrint(): String {
      val annotationString = toString()
      return if (annotationClass.qualifiedName == "com.google.inject.internal.Element") {
        if (annotationString.contains("type=MULTIBINDER")) {
          "Multibinder"
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

    private fun Key<*>.prettyPrintAnnotation(): String? {
      return annotation?.prettyPrint() ?: maybeExtractAnnotationFromKey(toString())
    }

    private fun maybeExtractAnnotationFromKey(keyToString: String): String? {
      val matchResult = keyAnnotationRegex.find(keyToString)
      val annotation = matchResult?.groupValues?.get(1) ?: ""
      return if (annotation == "[none") {
        null
      } else {
        annotation
      }
    }

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
}
