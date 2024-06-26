package misk.web.metadata.guice

import com.google.inject.Binding
import com.google.inject.Injector
import com.google.inject.Key
import jakarta.inject.Inject
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import misk.web.metadata.toFormattedJson
import wisp.moshi.adapter
import wisp.moshi.defaultKotlinMoshi

internal data class GuiceMetadata(
  val guice: GuiceMetadataProvider.Metadata
) : Metadata(
  metadata = guice,
  prettyPrint = "Total Bindings: ${guice.all.size}\n\n" + defaultKotlinMoshi
    .adapter<Map<String, Map<String, String>>>()
    .toFormattedJson(guice.grouped),
  descriptionString = "Direct injection bindings, powered by Guice. This metadata is work-in-progress."
)

internal class GuiceMetadataProvider : MetadataProvider<GuiceMetadata> {
  @Inject lateinit var injector: Injector

  override val id = "guice"

  internal data class Metadata(
    val all: Map<String, String>,
    val grouped: Map<String, Map<String, String>>,
  )

  val allBindings by lazy {
    injector.allBindings
  }

  val ambiguousNames by lazy {
    allBindings.keys
      .groupBy { it.typeLiteral.rawType.simpleName }
      .map { entry ->
        entry.key to entry.value
          .map { it.typeLiteral.rawType.packageName }
          .toSet()
      }
      .filter { it.second.size > 1 }
      .toMap()
  }

  val unambiguousPackages by lazy {
    allBindings.filter { it.key.typeLiteral.rawType.simpleName !in ambiguousNames.keys }
      .keys
      .map { it.typeLiteral.rawType.packageName }
      .toSet()
  }

  override fun get(): GuiceMetadata {
    val raw = allBindings
      .map { it.key.prettyPrint() to it.value.prettyPrint() }
      .toMap()
      .toSortedMap()

    val all = raw.entries.groupBy {
      if (it.key.contains(" / ")) it.key.split(" / ").first() else "default"
    }.map {
      it.key to it.value.associate { it.key.split(" / ").last() to it.value }
    }.toMap()

    return GuiceMetadata(
      Metadata(
        all = allBindings.map { it.key.toString() to it.value.toString() }.toMap(),
        grouped = all
      )
    )
  }

  fun Key<*>.prettyPrint(): String {
    var pretty = typeLiteral.toString()

    pretty = pretty.stripAmbiguousPackages()

    return if (annotation != null) {
      if (annotation.annotationClass.qualifiedName == "com.google.inject.internal.Element") {
        if (annotation.toString().contains("type=MULTIBINDER")) {
          "Multibinder / List<$pretty>"
        } else if (annotation.toString().contains("type=MAPBINDER")) {
          var keyType = annotation.toString().split("keyType=").last().removeSuffix(")")
          keyType = keyType.stripAmbiguousPackages()
          "Mapbinder / Map<$keyType,$pretty>"
        } else {
          "$pretty, annotation=$annotation"
        }
      } else {
        "$pretty, annotation=$annotation"
      }
    } else {
      pretty
    }
  }

  private fun String.stripAmbiguousPackages(): String {
    var pretty = this

    // Protect ambiguous packages by changing . to - in package names
    ambiguousNames.forEach {
      it.value.forEach { pkg ->
        pretty = pretty.replace("$pkg.${it.key}", "${pkg.replace(".", "-")}.${it.key}")
      }
    }

    // Strip unambiguous packages
    unambiguousPackages.forEach {
      pretty = pretty.replace("$it.", "")
    }

    // Restore ambiguous packages - with .
    ambiguousNames.forEach {
      it.value.forEach { pkg ->
        pretty = pretty.replace("${pkg.replace(".", "-")}.${it.key}", "$pkg.${it.key}")
      }
    }

    // Strip specific groups that don't need to be unambiguous (ie. javax vs jakarta vs google inject annotations)
    val commonPackages = listOf("com.google.inject", "jakarta.inject", "javax.inject")
    commonPackages.forEach {
      pretty = pretty.replace("$it.", "")
    }

    return pretty
  }

  private data class BindingMetadata(
    val type: String,
    val source: String,
    val scope: String?,
    val provider: String?
  ) {
    override fun toString(): String {
      return "$type(source=$source, scope=$scope, provider=$provider)"
    }
  }

  fun Binding<*>.prettyPrint(): String {
    val bindingString = this.toString()

    val source = source.toString().removePrefix("class ")

    val scope = if (bindingString.contains("scope=")) {
      bindingString.split("scope=").last().split("}").first().split(",").first()
    } else {
      null
    }

    val provider = if (bindingString.contains("provider=")) {
      bindingString.split("provider=").last().split("}").first().split(",").first()
    } else {
      null
    }

    val type = this::class.simpleName ?: "Binding"

    return BindingMetadata(type, source, scope, provider).toString()
  }
}
