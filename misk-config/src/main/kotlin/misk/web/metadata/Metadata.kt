package misk.web.metadata

data class Metadata(
  /** Unique identifier for the type of metadata. Ie. "web-actions" or "config". */
  val id: String,
  /** Metadata object, should be a data class for easy built-in serialization to JSON. */
  val metadata: Any,
)
