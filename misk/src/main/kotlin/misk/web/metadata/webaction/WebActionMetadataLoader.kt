package misk.web.metadata.webaction

import com.google.inject.Provider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.web.jetty.WebActionsServlet
import misk.web.metadata.Metadata

@Singleton
class WebActionMetadataLoader @Inject internal constructor(
  private val servletProvider: Provider<WebActionsServlet>
) : Provider<Metadata<*>> {
  override fun get() = WebActionsMetadata(servletProvider.get().webActionsMetadata)
}
