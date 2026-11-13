@file:OptIn(ExperimentalMiskApi::class)

package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Icon
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesRequest
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import jakarta.inject.Inject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import misk.MiskTestingServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.mcp.action.McpPost
import misk.mcp.action.McpStreamManager
import misk.mcp.action.handleMessage
import misk.mcp.config.McpConfig
import misk.mcp.config.McpIconConfig
import misk.mcp.config.McpServerConfig
import misk.mcp.testing.asMcpStreamableHttpClient
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.RequestBody
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.sse.ServerSentEvent
import okhttp3.OkHttpClient
import org.junit.jupiter.api.BeforeEach

@MiskTest(startService = true)
internal class McpFeatureMetadataTest {
  @Suppress("unused")
  class PostAction @Inject constructor(private val mcpStreamManager: McpStreamManager) : WebAction {
    @McpPost
    suspend fun mcpPost(@RequestBody message: JSONRPCMessage, sendChannel: SendChannel<ServerSentEvent>) {
      mcpStreamManager.withSseChannel(sendChannel) { handleMessage(message) }
    }
  }

  @Suppress("unused")
  @MiskTestModule
  val module =
    object : KAbstractModule() {
      override fun configure() {
        install(
          McpServerModule.create(
            SERVER_NAME,
            McpConfig(
              mapOf(
                SERVER_NAME to
                  McpServerConfig(
                    version = "1.0.0",
                    title = "Metadata Server",
                    website_url = "https://example.com",
                    icons = listOf(McpIconConfig(src = ICON.src, mime_type = ICON.mimeType, sizes = ICON.sizes!!)),
                  )
              )
            ),
          )
        )
        install(McpToolModule.create<TitledTool>())
        install(McpPromptModule.create<TitledPrompt>())
        install(McpResourceModule.create<TitledResource>())
        install(McpResourceTemplateModule.create<TitledResourceTemplate>())
        install(WebActionModule.create<PostAction>())
        install(WebServerTestingModule())
        install(MiskTestingServiceModule())
      }
    }

  @Inject private lateinit var jettyService: JettyService

  private lateinit var mcpClient: Client

  @BeforeEach
  fun setUp() = runBlocking { mcpClient = OkHttpClient().asMcpStreamableHttpClient(jettyService.httpServerUrl, "/mcp") }

  @Test
  fun `initialize reports server title, website and icons`() {
    val serverInfo = assertNotNull(mcpClient.serverVersion)
    assertEquals("Metadata Server", serverInfo.title)
    assertEquals("https://example.com", serverInfo.websiteUrl)
    assertEquals(listOf(ICON), serverInfo.icons)
  }

  @Test
  fun `completions capability is not advertised without completion providers`() {
    assertNull(assertNotNull(mcpClient.serverCapabilities).completions)
  }

  @Test
  fun `list responses carry feature titles and icons`() = runBlocking {
    val tool = mcpClient.listTools().tools.single()
    assertEquals("Titled Tool", tool.title)
    assertEquals(listOf(ICON), tool.icons)

    val prompt = mcpClient.listPrompts().prompts.single()
    assertEquals("Titled Prompt", prompt.title)
    assertEquals(listOf(ICON), prompt.icons)

    val resource = mcpClient.listResources().resources.single()
    assertEquals("Titled Resource", resource.title)
    assertEquals(listOf(ICON), resource.icons)

    val template = mcpClient.listResourceTemplates(ListResourceTemplatesRequest()).resourceTemplates.single()
    assertEquals("Titled Template", template.title)
    assertEquals(listOf(ICON), template.icons)
  }

  class TitledTool @Inject constructor() : McpToolEmptyInput() {
    override val name = "titled_tool"
    override val description = "A tool with a title and icons"
    override val title = "Titled Tool"
    override val icons = listOf(ICON)

    override suspend fun handle() = ToolResult(TextContent("ok"))
  }

  class TitledPrompt @Inject constructor() : McpPrompt {
    override val name = "titled_prompt"
    override val description = "A prompt with a title and icons"
    override val arguments = emptyList<PromptArgument>()
    override val title = "Titled Prompt"
    override val icons = listOf(ICON)

    override suspend fun handler(request: GetPromptRequest) = GetPromptResult(messages = emptyList())
  }

  class TitledResource @Inject constructor() : McpResource {
    override val uri = "test://titled"
    override val name = "titled_resource"
    override val description = "A resource with a title and icons"
    override val title = "Titled Resource"
    override val icons = listOf(ICON)

    override suspend fun handler(request: ReadResourceRequest) = ReadResourceResult(contents = emptyList())
  }

  class TitledResourceTemplate @Inject constructor() : McpResourceTemplate {
    override val uriTemplate = "test://titled/{id}"
    override val name = "titled_template"
    override val description = "A resource template with a title and icons"
    override val title = "Titled Template"
    override val icons = listOf(ICON)

    override suspend fun handler(request: ReadResourceRequest, variables: Map<String, String>) =
      ReadResourceResult(contents = emptyList())
  }

  companion object {
    private const val SERVER_NAME = "mcp-feature-metadata-test-server"
    private val ICON = Icon(src = "https://example.com/icon.png", mimeType = "image/png", sizes = listOf("48x48"))
  }
}
