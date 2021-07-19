package misk.web.metadata.webaction

import misk.web.jetty.WebActionsServlet
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class WebActionMetadataList @Inject internal constructor(
  private val servletProvider: Provider<WebActionsServlet>
) {
  fun get() = servletProvider.get().webActionsMetadata
}
