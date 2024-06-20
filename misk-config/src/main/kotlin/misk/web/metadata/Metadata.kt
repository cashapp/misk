package misk.web.metadata

import com.squareup.moshi.JsonAdapter
import kotlinx.html.TagConsumer
import misk.tailwind.components.AlertInfoHighlight

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
}

fun <T> JsonAdapter<T>.toFormattedJson(value: T): String = serializeNulls().indent("  ").toJson(value)
