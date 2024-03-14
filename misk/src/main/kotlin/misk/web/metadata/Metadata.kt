package misk.web.metadata

/** Metadata is a generic interface for associating metadata with a resource. */
interface Metadata<T : Any> {
  val id: String
  val metadata: T
  val metadataClass: Class<T>
}
