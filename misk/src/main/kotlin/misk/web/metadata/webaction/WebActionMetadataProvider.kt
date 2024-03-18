package misk.web.metadata.webaction

import com.google.inject.Provider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.web.jetty.WebActionsServlet

@Singleton
class WebActionMetadataProvider @Inject internal constructor(
  private val servletProvider: Provider<WebActionsServlet>
) : Provider<List<WebActionMetadata>> {
  override fun get() = servletProvider.get().webActionsMetadata
}
