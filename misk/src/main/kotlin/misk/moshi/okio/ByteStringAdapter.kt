package misk.moshi.okio

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import okio.ByteString
import java.lang.reflect.Type

/** JSON adapter converting [ByteString]s as base64 encoded strings */
internal class ByteStringAdapter : JsonAdapter<ByteString?>() {
  override fun toJson(writer: JsonWriter, value: ByteString?) {
    if (value != null) {
      writer.value(value.base64Url())
    } else {
      writer.nullValue()
    }
  }

  override fun fromJson(reader: JsonReader): ByteString? {
    val nextToken = reader.peek()
    return when (nextToken) {
      JsonReader.Token.NULL -> reader.nextNull()
      JsonReader.Token.STRING -> ByteString.decodeBase64(reader.nextString())
      else -> throw IllegalArgumentException("expected base64 string, found $nextToken")
    }
  }

  class Factory : JsonAdapter.Factory {
    override fun create(
      type: Type,
      annotations: Set<Annotation>,
      moshi: Moshi
    ): JsonAdapter<*>? {
      return if (type == ByteString::class.java) {
        ByteStringAdapter()
      } else null
    }

  }
}
