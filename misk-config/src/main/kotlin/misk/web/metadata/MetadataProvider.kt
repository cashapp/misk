package misk.web.metadata

import javax.inject.Provider

interface MetadataProvider<T: Metadata> : Provider<T> {
  /** Unique identifier for the type of metadata. Ie. "web-actions" or "config". */
  val id: String
}
