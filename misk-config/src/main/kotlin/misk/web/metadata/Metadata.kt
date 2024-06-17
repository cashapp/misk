package misk.web.metadata

import com.squareup.moshi.JsonAdapter

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
    .split(",").joinToString(",\n")
)

fun <T> JsonAdapter<T>.toFormattedJson(value: T): String = serializeNulls().indent("  ").toJson(value)
