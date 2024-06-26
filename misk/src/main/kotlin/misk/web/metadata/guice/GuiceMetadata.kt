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

data class GuiceMetadata(
  val allBindings: Map<String, String>
) : Metadata(
  metadata = allBindings,
  prettyPrint = defaultKotlinMoshi
    .adapter<Map<String, String>>()
    .toFormattedJson(allBindings),
  descriptionString = "Guice bindings."
)

class GuiceMetadataProvider : MetadataProvider<GuiceMetadata> {
  @Inject lateinit var injector: Injector

  override val id = "guice"

  override fun get(): GuiceMetadata {
    val allBindings = injector.allBindings

    return GuiceMetadata(
      allBindings = allBindings
        .map { it.key.prettyPrint(injector.allBindings) to it.value.prettyPrint() }
        .toMap()
        .toSortedMap()
    )
  }

  fun Key<*>.prettyPrint(allBindings: MutableMap<Key<*>, Binding<*>>): String {
    val ambiguousNames = allBindings.keys
      .groupBy { it.typeLiteral.rawType.simpleName }
      .map { entry ->
        entry.key to entry.value
          .map { it.typeLiteral.rawType.packageName }
          .toSet()
      }
      .filter { it.second.size > 1 }
      .toMap()

    val unambiguousPackages = allBindings.filter { it.key.typeLiteral.rawType.simpleName !in ambiguousNames.keys }
      .keys
      .map { it.typeLiteral.rawType.packageName }
      .toSet()

    var pretty = typeLiteral.toString()

    // Protect ambiguous packages by changing . to - in package names
    ambiguousNames.forEach {
      it.value.forEach { pkg ->
        pretty = pretty.replace("$pkg.${it.key}", "${pkg.replace(".", "-")}.${it.key}")
      }
    }

    // Cleanup unambiguous packages
    unambiguousPackages.forEach {
      pretty = pretty.replace("$it.", "")
    }

    // Restore ambiguous packages - with .
    ambiguousNames.forEach {
      it.value.forEach { pkg ->
        pretty = pretty.replace("${pkg.replace(".", "-")}.${it.key}", "$pkg.${it.key}")
      }
    }

    pretty = pretty
      .replace("? extends Map${"$"}Entry", "Entry")
      .replace("Map${"$"}Entry", "Entry")

    return if (annotation != null) {
      "$pretty, annotation=$annotation"
    } else {
      pretty
    }
  }

  fun Binding<*>.prettyPrint() = toString()
}
