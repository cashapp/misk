package misk.web.metadata

import com.squareup.moshi.JsonAdapter
import kotlinx.html.TagConsumer
import kotlinx.html.h3
import misk.tailwind.components.AlertInfoHighlight
import misk.tailwind.components.CodeBlock

open class Metadata @JvmOverloads constructor(
  /** Metadata object, should be a data class for easy built-in serialization to JSON. */
  val metadata: Any,

  /**
   * Pretty Print representation of the metadata used in the admin dashboard.
   * Most metadata should create a Moshi JSON adapter and use [toFormattedJson] to do this.
   */
  val prettyPrint: String = metadata.toString()
    // Improves readability of default data class toString() for admin dashboard if JSON or custom prettyPrint isn't provided
    .split("),").joinToString("),\n")
    .split(",").joinToString(",\n"),

  /** Description of what the metadata covers or administrator instructions. */
  val descriptionString: String = ""
) {
  /** HTML block for description. Can be overridden to show more complex UI or documentation. */
  open fun descriptionBlock(tagConsumer: TagConsumer<*>) = tagConsumer.apply {
    AlertInfoHighlight(message = descriptionString)
  }

  /** HTML block for the content. Can be overridden to show more complex UI than the default prettyPrint console output. */
  open fun contentBlock(tagConsumer: TagConsumer<*>) = tagConsumer.apply {
    h3("text-xl font-bold my-4") { +"""Metadata""" }
    CodeBlock(prettyPrint)
  }
}

private const val JSON_TRUNCATION_LIMIT = 100_000
fun <T> JsonAdapter<T>.toFormattedJson(value: T): String {
  val json = serializeNulls()
    .indent("  ")
    .toJson(value)
  return if (json.length < JSON_TRUNCATION_LIMIT) {
    json
  } else {
    json
      // Truncate JSON view to prevent tab from crashing for larger production service metadata.
      // Truncation is only in admin dashboard which uses this method in prettyPrint parameter.
      // Access via the API is still untruncated.
      .take(JSON_TRUNCATION_LIMIT) + "...\n\nWARN: Output has been truncated. Use the API directly to get full data."
  }
}
