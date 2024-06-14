package misk.web.metadata

import com.squareup.moshi.JsonAdapter

interface Metadata<T> {
  /** Metadata object, should be a data class for easy built-in serialization to JSON. */
  val metadata: T

  /** Metadata adapter to use for moshi JSON serialization. */
  val adapter: JsonAdapter<T>
}
