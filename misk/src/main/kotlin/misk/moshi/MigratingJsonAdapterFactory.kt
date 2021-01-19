package misk.moshi

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import java.lang.reflect.Type

internal class MigratingJsonAdapterFactory(
  val reader: JsonAdapter.Factory,
  val writer: JsonAdapter.Factory
) : JsonAdapter.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun create(
    type: Type,
    annotations: MutableSet<out Annotation>,
    moshi: Moshi
  ): JsonAdapter<*>? {
    var readerDelegate = reader.create(type, annotations, moshi)
    var writerDelegate = writer.create(type, annotations, moshi)
    if (readerDelegate == null && writerDelegate == null) {
      // This type is not supported by either. Don't do anything.
      return null
    }

    if (readerDelegate == null) {
      readerDelegate = moshi.nextAdapter<Any>(this, type, annotations)
    }

    if (writerDelegate == null) {
      writerDelegate = moshi.nextAdapter<Any>(this, type, annotations)
    }

    return MigratingJsonAdapter(
      readerAdapter = readerDelegate as JsonAdapter<Any>,
      writerAdapter = writerDelegate as JsonAdapter<Any>
    )
  }

  private class MigratingJsonAdapter<T>(
    private val readerAdapter: JsonAdapter<T>,
    private val writerAdapter: JsonAdapter<T>
  ) : JsonAdapter<T>() {
    override fun fromJson(reader: JsonReader): T? {
      return readerAdapter.fromJson(reader)
    }

    override fun toJson(writer: JsonWriter, value: T?) {
      return writerAdapter.toJson(writer, value)
    }
  }
}
