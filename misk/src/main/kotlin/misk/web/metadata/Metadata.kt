package misk.web.metadata

import javax.inject.Inject
import javax.inject.Provider

data class Metadata(
  /** Unique identifier for the type of metadata. Ie. "web-action" or "service-config" */
  val id: String,
  val metadata: Any,
)

class MetadataProvider<T>(
  private val id: String,
) : Provider<Metadata> {
  @Inject lateinit var provider: Provider<T>

  override fun get() = Metadata(id, provider.get() as Any)
}
