package misk.web.metadata

import com.google.inject.Provider

interface MetadataProvider<ST: Any, T: Metadata<ST>> : Provider<T> {
  /** Unique identifier for the type of metadata. Ie. "web-actions" or "config". */
  val id: String
}
