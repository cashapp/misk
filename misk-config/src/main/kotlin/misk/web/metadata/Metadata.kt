package misk.web.metadata

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonWriter
import okio.Buffer

open class Metadata(
  /** Metadata object, should be a data class for easy built-in serialization to JSON. */
  val metadata: Any,

  /** JSON representation of the metadata. */
  val formattedJsonString: String
)

fun <T> JsonAdapter<T>.toFormattedJson(value: T): String {
  val buffer = Buffer()
  val jsonWriter = JsonWriter.of(buffer)
  this.serializeNulls().indent("  ").toJson(jsonWriter, value)
  val json = buffer.readUtf8();
  return json
}
