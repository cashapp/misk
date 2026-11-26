@file:OptIn(ExperimentalMiskApi::class)

package misk.mcp

import com.google.inject.Module
import jakarta.inject.Inject
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import misk.annotation.ExperimentalMiskApi
import misk.mcp.McpProtocolTest.Companion.mcpTestModule
import misk.mcp.config.McpIconConfig
import misk.mcp.config.McpServerConfig
import misk.mcp.protocol.CallToolResult
import misk.mcp.protocol.CompleteRequest
import misk.mcp.protocol.CompleteRequestParams
import misk.mcp.protocol.CompleteResult
import misk.mcp.protocol.GetPromptRequest
import misk.mcp.protocol.GetPromptResult
import misk.mcp.protocol.Icon
import misk.mcp.protocol.Implementation
import misk.mcp.protocol.JSONRPCResponse
import misk.mcp.protocol.LATEST_LEGACY_PROTOCOL_VERSION
import misk.mcp.protocol.McpException
import misk.mcp.protocol.McpMetaKeys
import misk.mcp.protocol.McpMethod
import misk.mcp.protocol.PromptArgument
import misk.mcp.protocol.PromptReference
import misk.mcp.protocol.RPCError
import misk.mcp.protocol.Reference
import misk.mcp.protocol.ResourceTemplateReference
import misk.mcp.protocol.TextContent
import misk.mcp.testing.McpTestClient
import misk.mcp.testing.prompts.KotlinDeveloperPrompt
import misk.mcp.testing.resources.UserProfileResource
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class McpFeaturesTest {
  @MiskTestModule
  val module: Module =
    mcpTestModule(
      McpToolModule.create<ProgressTool>(),
      McpPromptModule.create<KotlinDeveloperPrompt>(),
      McpPromptModule.create<IconPrompt>(),
      McpResourceTemplateModule.create<UserProfileResource>(),
      McpCompletionModule.create<ProjectNameCompletions>(),
      config =
        McpServerConfig(
          "1.0.0",
          title = "Protocol Test",
          website_url = "https://example.com",
          icons = listOf(McpIconConfig(src = "https://example.com/icon.png", sizes = listOf("48x48"), theme = "dark")),
        ),
    )

  @Inject lateinit var jettyService: JettyService

  private val url by lazy { jettyService.httpServerUrl.resolve("/mcp")!! }
  private val modern by lazy { McpTestClient(OkHttpClient(), url) }
  private val legacy by lazy { McpTestClient(OkHttpClient(), url, LATEST_LEGACY_PROTOCOL_VERSION) }

  @Test
  fun `server info carries the configured title, website and icons`() = runBlocking {
    val expected =
      Implementation(
        name = McpProtocolTest.SERVER_NAME,
        version = "1.0.0",
        title = "Protocol Test",
        websiteUrl = "https://example.com",
        icons = listOf(Icon(src = "https://example.com/icon.png", sizes = listOf("48x48"), theme = Icon.Theme.Dark)),
      )

    val discovered = modern.request(McpMethod.SERVER_DISCOVER, null)["_meta"]!!.jsonObject[McpMetaKeys.SERVER_INFO]!!
    assertEquals(expected, discovered.decode<Implementation>())
    assertEquals(expected, legacy.initialize().serverInfo)
  }

  @Test
  fun `tools and prompts list their titles and icons`() = runBlocking {
    val tool = modern.listTools().tools.single()
    assertEquals("Progress", tool.title)
    assertEquals(listOf(ICON), tool.icons)

    val prompt = modern.listPrompts().prompts.single { it.name == "icon" }
    assertEquals("Icon Prompt", prompt.title)
    assertEquals(listOf(ICON), prompt.icons)
  }

  @Test
  fun `progress is reported in order before the result, in both eras`() = runBlocking {
    for (client in listOf(modern, legacy)) {
      val response = client.send(client.newRequest(McpMethod.TOOLS_CALL, progressCall(token = "t1")))

      val progress = response.notifications.map { it.params!! }
      assertThat(response.notifications.map { it.method }).containsOnly(McpMethod.NOTIFICATIONS_PROGRESS)
      assertEquals(listOf("1.0", "2.0"), progress.map { it["progress"]!!.jsonPrimitive.content })
      assertEquals("t1", progress.first()["progressToken"]!!.jsonPrimitive.content)
      assertEquals("halfway", progress.first()["message"]!!.jsonPrimitive.content)
      assertThat(response.messages.last()).isEqualTo(response.reply)
    }
  }

  @Test
  fun `progress is not reported without a progress token`() = runBlocking {
    val response = modern.send(modern.newRequest(McpMethod.TOOLS_CALL, progressCall(token = null)))

    assertThat(response.notifications).isEmpty()
    val result = (response.reply as JSONRPCResponse).result.decode<CallToolResult>()
    assertEquals("done", (result.content.single() as TextContent).text)
  }

  @Test
  fun `completions are capped at 100 values with the total`() = runBlocking {
    assertThat(modern.discover().capabilities.completions).isNotNull()

    val result = modern.complete(completeRequest(PromptReference("Kotlin Developer"), value = "p"))

    assertEquals(100, result.completion.values.size)
    assertEquals("p0", result.completion.values.first())
    assertEquals(150, result.completion.total)
    assertEquals(true, result.completion.hasMore)
  }

  @Test
  fun `references without a provider complete to nothing, and unknown references are invalid`() = runBlocking {
    for (client in listOf(modern, legacy)) {
      assertEquals(
        CompleteResult.Completion(values = emptyList(), total = 0, hasMore = false),
        client.complete(completeRequest(ResourceTemplateReference("users://{userId}/profile"))).completion,
      )

      val error = runCatching { client.complete(completeRequest(PromptReference("missing"))) }.exceptionOrNull()
      assertEquals(RPCError.ErrorCode.INVALID_PARAMS, (error as McpException).code)
    }
  }

  private fun progressCall(token: String?) = buildJsonObject {
    put("name", "progress")
    putJsonObject("arguments") {}
    if (token != null) putJsonObject("_meta") { put(McpMetaKeys.PROGRESS_TOKEN, token) }
  }

  private fun completeRequest(ref: Reference, value: String = "") =
    CompleteRequest(CompleteRequestParams(argument = CompleteRequestParams.Argument("Project Name", value), ref = ref))

  class ProgressTool @Inject constructor() : McpToolEmptyInput() {
    override val name = "progress"
    override val description = "Reports progress twice"
    override val title = "Progress"
    override val icons = listOf(ICON)

    override suspend fun handle(): ToolResult {
      reportProgress(1.0, total = 2.0, message = "halfway")
      reportProgress(2.0, total = 2.0)
      return ToolResult(TextContent("done"))
    }
  }

  class IconPrompt @Inject constructor() : McpPrompt {
    override val name = "icon"
    override val description = "A prompt with an icon"
    override val title = "Icon Prompt"
    override val icons = listOf(ICON)
    override val arguments = emptyList<PromptArgument>()

    override suspend fun handler(request: GetPromptRequest) = GetPromptResult(messages = emptyList())
  }

  class ProjectNameCompletions @Inject constructor() : McpCompletionProvider {
    override val ref = PromptReference("Kotlin Developer")

    override suspend fun complete(request: CompleteRequest) = List(150) { "${request.argument.value}$it" }
  }

  companion object {
    val ICON = Icon(src = "data:image/svg+xml;base64,PHN2Zy8+", mimeType = "image/svg+xml")
  }
}
