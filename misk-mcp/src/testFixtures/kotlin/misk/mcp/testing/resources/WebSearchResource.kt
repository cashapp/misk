package misk.mcp.testing.resources

import jakarta.inject.Inject
import misk.annotation.ExperimentalMiskApi
import misk.mcp.McpResource
import misk.mcp.protocol.ReadResourceRequest
import misk.mcp.protocol.ReadResourceResult
import misk.mcp.protocol.TextResourceContents

@OptIn(ExperimentalMiskApi::class)
class WebSearchResource @Inject constructor() : McpResource {
  override val uri = "https://search.com/"
  override val name = "Web Search"
  override val description = "Web search engine"

  override suspend fun handler(request: ReadResourceRequest) =
    ReadResourceResult(
      contents = listOf(TextResourceContents("Placeholder content for ${request.uri}", request.uri, "text/html"))
    )
}
