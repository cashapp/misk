package misk.web.metadata.webaction

import com.google.inject.Provider
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.adapter
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.web.jetty.WebActionsServlet
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import wisp.moshi.defaultKotlinMoshi

@OptIn(ExperimentalStdlibApi::class)
data class WebActionsMetadata(
  override val metadata: List<WebActionMetadata>,
  override val adapter: JsonAdapter<List<WebActionMetadata>> = defaultKotlinMoshi.adapter<List<WebActionMetadata>>(),
) : Metadata<List<WebActionMetadata>>

@Singleton
class WebActionsMetadataProvider : MetadataProvider<List<WebActionMetadata>, WebActionsMetadata> {
  @Inject private lateinit var servletProvider: Provider<WebActionsServlet>

  override val id: String = "web-actions"

  override fun get() = WebActionsMetadata(servletProvider.get().webActionsMetadata)
}
