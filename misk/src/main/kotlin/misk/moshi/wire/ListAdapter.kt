package misk.moshi.wire

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

/** Handles JSON marshalling / unmarshalling for type-safe lists */
internal class ListAdapter(private val elementAdapter: JsonAdapter<Any?>) :
  JsonAdapter<List<Any?>>() {
  override fun fromJson(reader: JsonReader): List<Any?>? {
    val nextToken = reader.peek()
    if (nextToken == JsonReader.Token.NULL) return reader.nextNull()
    check(nextToken == JsonReader.Token.BEGIN_ARRAY) {
      "expected [; found ${reader.peek()}"
    }

    val elements = mutableListOf<Any?>()
    reader.beginArray()
    while (reader.hasNext()) {
      val value = elementAdapter.fromJson(reader)
      elements.add(value)
    }
    reader.endArray()
    return elements
  }

  override fun toJson(writer: JsonWriter, value: List<Any?>?) {
    if (value == null) {
      writer.nullValue()
    } else {
      writer.beginArray()
      value.forEach { elementAdapter.toJson(writer, it) }
      writer.endArray()
    }
  }
}
