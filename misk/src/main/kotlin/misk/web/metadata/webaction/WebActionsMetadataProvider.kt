package misk.web.metadata.webaction

import com.google.inject.Provider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.web.jetty.WebActionsServlet
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import misk.web.metadata.toFormattedJson
import wisp.moshi.adapter
import wisp.moshi.defaultKotlinMoshi

data class WebActionsMetadata(
  val webActions: List<WebActionMetadata>
) : Metadata(metadata = webActions, prettyPrint = defaultKotlinMoshi
  .adapter<List<WebActionMetadata>>()
  .toFormattedJson(webActions))

@Singleton
class WebActionsMetadataProvider : MetadataProvider<WebActionsMetadata> {
  @Inject private lateinit var servletProvider: Provider<WebActionsServlet>

  override val id: String = "web-actions"

  override fun get() = WebActionsMetadata(servletProvider.get().webActionsMetadata)
}
