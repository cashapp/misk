package misk.moshi.wire

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

/** Handles JSON marshalling / unmarshalling for type-safe maps */
internal class MapAdapter(private val keyConverter: (String) -> Any, private val valueAdapter: JsonAdapter<Any?>) :
  JsonAdapter<Map<Any, Any>>() {
  override fun fromJson(reader: JsonReader): Map<Any, Any>? {
    val nextToken = reader.peek()
    if (nextToken == JsonReader.Token.NULL) return reader.nextNull()
    check(nextToken == JsonReader.Token.BEGIN_OBJECT) { "expected {, found $nextToken" }

    val results = mutableMapOf<Any, Any>()
    reader.beginObject()
    while (reader.hasNext()) {
      val keyName = reader.nextName()
      val key = keyConverter(keyName)
      val value = valueAdapter.fromJson(reader)
      if (value != null) results[key] = value
    }
    reader.endObject()
    return results
  }

  override fun toJson(writer: JsonWriter, value: Map<Any, Any>?) {
    if (value == null) {
      writer.nullValue()
    } else {
      writer.beginObject()
      value.forEach { (key, value) ->
        writer.name(key.toString())
        valueAdapter.toJson(writer, value)
      }
      writer.endObject()
    }
  }
}
