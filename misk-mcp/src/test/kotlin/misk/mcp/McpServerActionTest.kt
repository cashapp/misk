@file:OptIn(ExperimentalMiskApi::class)

package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.ListPromptsRequest
import io.modelcontextprotocol.kotlin.sdk.ListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.decodeFromJsonElement
import misk.MiskTestingServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.mcp.action.McpPost
import misk.mcp.action.McpStreamManager
import misk.mcp.config.McpConfig
import misk.mcp.config.McpServerConfig
import misk.mcp.internal.McpJson
import misk.mcp.prompts.KotlinDeveloperPrompt
import misk.mcp.resources.WebSearchResource
import misk.mcp.testing.asMcpClient
import misk.mcp.tools.CalculatorTool
import misk.mcp.tools.CalculatorToolInput.Operation
import misk.mcp.tools.CalculatorToolOutput
import misk.mcp.tools.HierarchicalTool
import misk.mcp.tools.HierarchicalToolOutput
import misk.mcp.tools.KotlinSdkTool
import misk.mcp.tools.ThrowingTool
import misk.mcp.tools.callCalculatorTool
import misk.mcp.tools.callHierarchicalTool
import misk.mcp.tools.callThrowingTool
import misk.metrics.summaryCount
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.RequestBody
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.sse.ServerSentEvent
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@MiskTest(startService = true)
internal class McpServerActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module = McpServerActionTestModule()

  @Inject
  private lateinit var jettyService: JettyService
  @Inject
  private lateinit var registry: CollectorRegistry

  private val okHttpClient = OkHttpClient()

  @Test
  fun `test ListTools`() = runBlocking {
    val mcpClient = okHttpClient.asMcpClient(jettyService.httpServerUrl, "/mcp")
    val request = ListToolsRequest()
    val response = mcpClient.listTools(request)
    assertEquals(
      expected = 4,
      actual = response.tools.size,
      message = "Expecting four tools to be registered",
    )

    // Check calculator tool
    val calculatorTool = response.tools.find { it.name == "calculator" }
    assertNotNull(calculatorTool)
    assertEquals(
      expected = "calculator",
      actual = calculatorTool.name,
      message = "Expected the calculator tool name to be 'calculator'",
    )
    assertEquals(
      expected = """A simple calculator that can perform basic arithmetic operations such as 
      |addition, subtraction, multiplication, and division on two integers. In the input, 
      |the operation will be interpreted as `firstTerm <operation> secondTerm`. 
      |""".trimMargin(),
      actual = calculatorTool.description,
      message = "Expected the correct description for calculator tool",
    )

    // Check calculator tool input schema
    assertNotNull(calculatorTool.inputSchema)
    assertEquals(
      expected = "object",
      actual = calculatorTool.inputSchema.type,
      message = "Expected input schema type to be 'object'",
    )
    assertNotNull(calculatorTool.inputSchema.properties)

    // Verify required fields for calculator
    val calculatorRequired = calculatorTool.inputSchema.required
    assertNotNull(calculatorRequired)
    assertEquals(3, calculatorRequired.size)
    assertContains(calculatorRequired, "operation")
    assertContains(calculatorRequired, "first_term")
    assertContains(calculatorRequired, "second_term")

    // Check kotlin-sdk-tool
    val kotlinSdkTool = response.tools.find { it.name == "kotlin-sdk-tool" }
    assertNotNull(kotlinSdkTool)
    assertEquals(
      expected = "kotlin-sdk-tool",
      actual = kotlinSdkTool.name,
      message = "Expected the kotlin-sdk-tool name to be 'kotlin-sdk-tool'",
    )
    assertEquals(
      expected = "A test tool",
      actual = kotlinSdkTool.description,
      message = "Expected the correct description for kotlin-sdk-tool",
    )

    // Check kotlin-sdk-tool input schema
    assertNotNull(kotlinSdkTool.inputSchema)
    assertEquals(
      expected = "object",
      actual = kotlinSdkTool.inputSchema.type,
      message = "Expected input schema type to be 'object'",
    )
  }

  @Test
  fun `test add two integers`() = runBlocking {
    val mcpClient = okHttpClient.asMcpClient(jettyService.httpServerUrl, "/mcp")

    val response = mcpClient.callCalculatorTool(5, 3, Operation.ADD)

    val structuredResult = McpJson.decodeFromJsonElement<CalculatorToolOutput>(
      assertNotNull(response?.structuredContent),
    )


    assertEquals(
      expected = 8,
      actual = structuredResult.result,
      message = "Expected the calculator tool to return the correct addition result",
    )
  }

  @Test
  fun `test ListPrompts`() = runBlocking {
    val mcpClient = okHttpClient.asMcpClient(jettyService.httpServerUrl, "/mcp")
    val request = ListPromptsRequest()
    val response = mcpClient.listPrompts(request)

    assertEquals(
      expected = 1,
      actual = response.prompts.size,
      message = "Expecting one prompt to be registered",
    )

    val kotlinDeveloperPrompt = response.prompts.firstOrNull()
    assertNotNull(kotlinDeveloperPrompt)
    assertEquals(
      expected = "Kotlin Developer",
      actual = kotlinDeveloperPrompt.name,
      message = "Expected the Kotlin Developer prompt to be registered",
    )
    assertEquals(
      expected = "Develop small kotlin applications",
      actual = kotlinDeveloperPrompt.description,
      message = "Expected the correct description for Kotlin Developer prompt",
    )

    // Check arguments
    assertEquals(
      expected = 1,
      actual = kotlinDeveloperPrompt.arguments?.size,
      message = "Expected one argument for Kotlin Developer prompt",
    )

    val projectNameArg = kotlinDeveloperPrompt.arguments?.firstOrNull()
    assertNotNull(projectNameArg)
    assertEquals("Project Name", projectNameArg.name)
    assertEquals("Project name for the new project", projectNameArg.description)
    assertTrue(projectNameArg.required ?: false)
  }

  @Test
  fun `test GetPrompt with arguments`() = runBlocking {
    val mcpClient = okHttpClient.asMcpClient(jettyService.httpServerUrl, "/mcp")
    val request = GetPromptRequest(
      name = "Kotlin Developer",
      arguments = mapOf("Project Name" to "TestProject"),
    )
    val response = mcpClient.getPrompt(request)

    assertNotNull(response)
    assertEquals(
      expected = "Description for Kotlin Developer",
      actual = response.description,
    )

    assertEquals(
      expected = 1,
      actual = response.messages.size,
      message = "Expected one message in the prompt response",
    )

    val message = response.messages.firstOrNull()
    assertNotNull(message)
    assertEquals("user", message.role.toString())

    val textContent = message.content as? io.modelcontextprotocol.kotlin.sdk.TextContent
    assertNotNull(textContent)
    assertContains(
      textContent.text!!,
      "Develop a kotlin project named <name>TestProject</name>",
      message = "Expected the project name to be included in the prompt message",
    )
  }

  @Test
  fun `test ListResources`() = runBlocking {
    val mcpClient = okHttpClient.asMcpClient(jettyService.httpServerUrl, "/mcp")
    val request = ListResourcesRequest()
    val response = mcpClient.listResources(request)

    assertEquals(
      expected = 1,
      actual = response.resources.size,
      message = "Expecting one resource to be registered",
    )

    val webSearchResource = response.resources.firstOrNull()
    assertNotNull(webSearchResource)
    assertEquals(
      expected = "https://search.com/",
      actual = webSearchResource.uri,
      message = "Expected the correct URI for Web Search resource",
    )
    assertEquals(
      expected = "Web Search",
      actual = webSearchResource.name,
      message = "Expected the Web Search resource to be registered",
    )
    assertEquals(
      expected = "Web search engine",
      actual = webSearchResource.description,
      message = "Expected the correct description for Web Search resource",
    )
  }

  @Test
  fun `test ReadResource`() = runBlocking {
    val mcpClient = okHttpClient.asMcpClient(jettyService.httpServerUrl, "/mcp")
    val request = ReadResourceRequest(uri = "https://search.com/")
    val response = mcpClient.readResource(request)

    assertNotNull(response)
    assertEquals(
      expected = 1,
      actual = response.contents.size,
      message = "Expected one content item in the resource response",
    )

    val content = response.contents.firstOrNull() as? io.modelcontextprotocol.kotlin.sdk.TextResourceContents
    assertNotNull(content)
    assertEquals(
      expected = "https://search.com/",
      actual = content.uri,
      message = "Expected the correct URI in the resource content",
    )
    assertEquals(
      expected = "text/html",
      actual = content.mimeType,
      message = "Expected the correct MIME type for the resource content",
    )
    assertContains(
      content.text,
      "Placeholder content for https://search.com/",
      message = "Expected the placeholder content to be returned",
    )
  }

  @Test
  fun `test success metrics`(): Unit = runBlocking {
    val mcpClient = okHttpClient.asMcpClient(jettyService.httpServerUrl, "/mcp")

    mcpClient.callCalculatorTool(4, 2, Operation.DIVIDE)

    assertThat(
      registry.summaryCount(
        "mcp_tool_handler_latency",
        "server_name" to "mcp-server-action-test-server",
        "tool_name" to "calculator",
        "tool_outcome" to "Success",
      )
    ).isEqualTo(1.0)
  }

  @Test
  fun `test error metrics`(): Unit = runBlocking {
    val mcpClient = okHttpClient.asMcpClient(jettyService.httpServerUrl, "/mcp")

    // Divide by zero
    mcpClient.callCalculatorTool(4, 0, Operation.DIVIDE)
    
    assertThat(
      registry.summaryCount(
        "mcp_tool_handler_latency",
        "server_name" to "mcp-server-action-test-server",
        "tool_name" to "calculator",
        "tool_outcome" to "Error",
      )
    ).isEqualTo(1.0)
  }

  @Test
  fun `test exception metrics`(): Unit = runBlocking {
    val mcpClient = okHttpClient.asMcpClient(jettyService.httpServerUrl, "/mcp")

    assertThrows<IllegalStateException> { mcpClient.callThrowingTool() }
    
    assertThat(
      registry.summaryCount(
        "mcp_tool_handler_latency",
        "server_name" to "mcp-server-action-test-server",
        "tool_name" to "throwing",
        "tool_outcome" to "Exception",
      )
    ).isEqualTo(1.0)
  }

  @Test
  fun `test hierarchical tool`(): Unit = runBlocking {
    val mcpClient = okHttpClient.asMcpClient(jettyService.httpServerUrl, "/mcp")

    val response = mcpClient.callHierarchicalTool()

    val structuredResult = McpJson.decodeFromJsonElement<HierarchicalToolOutput>(
      assertNotNull(response?.structuredContent),
    )
    
    assertThat(structuredResult).isEqualTo(HierarchicalToolOutput("test"))
  }
}

val mcpServerActionTestConfig = McpConfig(
  buildMap {
    put(
      "mcp-server-action-test-server",
      McpServerConfig(
        version = "1.0.0",
      ),
    )
  },
)

@OptIn(ExperimentalMiskApi::class)
@Suppress("unused")
@Singleton
class McpServerActionTestModulePostAction @Inject constructor(private val mcpStreamManager: McpStreamManager) :
  WebAction {
  @McpPost
  suspend fun mcpPost(@RequestBody message: JSONRPCMessage, sendChannel: SendChannel<ServerSentEvent>) {
    mcpStreamManager.withResponseChannel(sendChannel) { handleMessage(message) }
  }
}

class McpServerActionTestModule : KAbstractModule() {
  override fun configure() {
    install(McpServerModule.create("mcp-server-action-test-server", mcpServerActionTestConfig))
    install(WebActionModule.create<McpServerActionTestModulePostAction>())
    install(McpToolModule.create<CalculatorTool>())
    install(McpToolModule.create<KotlinSdkTool>())
    install(McpToolModule.create<ThrowingTool>())
    install(McpToolModule.create<HierarchicalTool>())
    install(McpPromptModule.create<KotlinDeveloperPrompt>())
    install(McpResourceModule.create<WebSearchResource>())

    install(WebServerTestingModule())
    install(MiskTestingServiceModule())
  }
}

