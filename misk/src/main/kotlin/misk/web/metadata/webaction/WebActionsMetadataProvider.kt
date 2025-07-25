package misk.web.metadata.webaction

import com.google.inject.Provider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.TagConsumer
import misk.moshi.adapter
import misk.tailwind.components.AlertInfo
import misk.tailwind.components.AlertInfoHighlight
import misk.web.jetty.WebActionsServlet
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import misk.web.metadata.toFormattedJson
import wisp.moshi.defaultKotlinMoshi

data class WebActionsMetadata(
  val webActions: List<WebActionMetadata>
) : Metadata(
  metadata = webActions,
  prettyPrint = defaultKotlinMoshi
    .adapter<List<WebActionMetadata>>()
    .toFormattedJson(webActions),
) {
  override fun descriptionBlock(tagConsumer: TagConsumer<*>): TagConsumer<*> = tagConsumer.apply {
    AlertInfoHighlight(
      message = "Includes metadata on all bound web action endpoints including their authentication, annotations, paths, and request/response types. This powers the Web Actions admin dashboard tab.",
      label = "Admin Dashboard Tab",
      link = "/_admin/web-actions/",
    )
  }
}

@Singleton
class WebActionsMetadataProvider : MetadataProvider<WebActionsMetadata> {
  @Inject private lateinit var servletProvider: Provider<WebActionsServlet>

  override val id: String = "web-actions"

  val metadata by lazy { WebActionsMetadata(servletProvider.get().webActionsMetadata) }

  override fun get() = metadata
}
