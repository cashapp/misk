package misk.web.metadata.webaction

import com.google.inject.Provider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.web.jetty.WebActionsServlet
import misk.web.metadata.Metadata

data class WebActionsMetadata(
  val webActions: List<WebActionMetadata>
) : Metadata(id = "web-actions", metadata = webActions)

@Singleton
class WebActionsMetadataProvider : Provider<WebActionsMetadata> {
  @Inject private lateinit var servletProvider: Provider<WebActionsServlet>
  override fun get() = WebActionsMetadata(servletProvider.get().webActionsMetadata)
}
