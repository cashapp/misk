package misk.mcp.testing.resources

import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import jakarta.inject.Inject
import misk.annotation.ExperimentalMiskApi
import misk.mcp.McpResource

@OptIn(ExperimentalMiskApi::class)
class WebSearchResource @Inject constructor() : McpResource {
  override val uri = "https://search.com/"
  override val name = "Web Search"
  override val description = "Web search engine"

  override suspend fun handler(request: ReadResourceRequest) = ReadResourceResult(
    contents = listOf(
      TextResourceContents("Placeholder content for ${request.uri}", request.uri, "text/html"),
    ),
  )
}
