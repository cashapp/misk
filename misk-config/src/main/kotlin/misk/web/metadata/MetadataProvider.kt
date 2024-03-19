package misk.web.metadata

import javax.inject.Inject
import javax.inject.Provider

/** Generic provider which allows for easy binding of metadata assuming there is a provider of the type available. */
class MetadataProvider<T: Any>(
  private val id: String,
) : Provider<Metadata> {
  @Inject lateinit var provider: Provider<T>
  override fun get() = Metadata(id, provider.get() as Any)
}
