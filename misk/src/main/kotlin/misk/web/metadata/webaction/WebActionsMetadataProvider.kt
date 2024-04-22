package misk.web.metadata.webaction

import com.google.inject.Provider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.web.jetty.WebActionsServlet
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider

data class WebActionsMetadata(
  val webActions: List<WebActionMetadata>
) : Metadata(metadata = webActions)

@Singleton
class WebActionsMetadataProvider : MetadataProvider<WebActionsMetadata> {
  @Inject private lateinit var servletProvider: Provider<WebActionsServlet>

  override val id: String = "web-actions"

  override fun get() = WebActionsMetadata(servletProvider.get().webActionsMetadata)
}
