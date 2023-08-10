package misk.web.metadata.webaction

import misk.web.jetty.WebActionsServlet
import jakarta.inject.Inject
import com.google.inject.Provider
import jakarta.inject.Singleton

@Singleton
class WebActionMetadataList @Inject internal constructor(
  private val servletProvider: Provider<WebActionsServlet>
) {
  fun get() = servletProvider.get().webActionsMetadata
}
