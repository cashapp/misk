@file:OptIn(ExperimentalMiskApi::class)

package misk.mcp

import com.google.inject.Module
import com.google.inject.util.Modules
import io.modelcontextprotocol.kotlin.sdk.CreateElicitationResult
import io.modelcontextprotocol.kotlin.sdk.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.ListPromptsRequest
import io.modelcontextprotocol.kotlin.sdk.ListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import misk.MiskTestingServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.mcp.action.McpPost
import misk.mcp.action.McpStreamManager
import misk.mcp.action.McpWebSocket
import misk.mcp.action.handleMessage
import misk.mcp.config.McpConfig
import misk.mcp.config.McpServerConfig
import misk.mcp.testing.prompts.KotlinDeveloperPrompt
import misk.mcp.testing.resources.WebSearchResource
import misk.mcp.testing.asMcpStreamableHttpClient
import misk.mcp.testing.asMcpWebSocketClient
import misk.mcp.testing.tools.CalculatorTool
import misk.mcp.testing.tools.CalculatorToolInput.Operation
import misk.mcp.testing.tools.CalculatorToolOutput
import misk.mcp.testing.tools.GetNicknameRequest
import misk.mcp.testing.tools.HierarchicalTool
import misk.mcp.testing.tools.HierarchicalToolOutput
import misk.mcp.testing.tools.KotlinSdkTool
import misk.mcp.testing.tools.NicknameElicitationTool
import misk.mcp.testing.tools.ThrowingTool
import misk.mcp.testing.tools.VersionMetadata
import misk.mcp.testing.tools.callCalculatorTool
import misk.mcp.testing.tools.callHierarchicalTool
import misk.mcp.testing.tools.callNicknameTool
import misk.mcp.testing.tools.callThrowingTool
import misk.metrics.summaryCount
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.RequestBody
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.actions.WebSocket
import misk.web.jetty.JettyService
import misk.web.sse.ServerSentEvent
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


internal abstract class McpServerActionTest {

  @MiskTest(startService = true)
  class McpStreamableHttpServerActionTest : McpServerActionTest() {
    @OptIn(ExperimentalMiskApi::class)
    @Suppress("unused")
    @Singleton
    class McpServerActionTestPostAction @Inject constructor(private val mcpStreamManager: McpStreamManager) :
      WebAction {
      @McpPost
      suspend fun mcpPost(@RequestBody message: JSONRPCMessage, sendChannel: SendChannel<ServerSentEvent>) {
        mcpStreamManager.withSseChannel(sendChannel) { handleMessage(message) }
      }
    }

    @Suppress("unused")
    @MiskTestModule
    val module: Module = Modules.combine(
      mcpServerTestModule,
      WebActionModule.create<McpServerActionTestPostAction>()
    )

    @BeforeEach
    fun setUp() = runBlocking {
      mcpClient = OkHttpClient().asMcpStreamableHttpClient(
        jettyService.httpServerUrl,
        "/mcp",
        supportsElicitation = true
      )
    }
  }

  @MiskTest(startService = true)
  class McpWebSocketServerActionTest : McpServerActionTest() {
    @OptIn(ExperimentalMiskApi::class)
    @Suppress("unused")
    @Singleton
    class McpWebSocketServerActionTestAction @Inject constructor(
      private val mcpStreamManager: McpStreamManager
    ) : WebAction {
      @McpWebSocket
      fun handle(webSocket: WebSocket) = mcpStreamManager.withWebSocket(webSocket)
    }

    @Suppress("unused")
    @MiskTestModule
    val module: Module = Modules.combine(
      mcpServerTestModule,
      WebActionModule.create<McpWebSocketServerActionTestAction>()
    )

    @BeforeEach
    fun setUp() = runBlocking {
      mcpClient = OkHttpClient().asMcpWebSocketClient(
        jettyService.httpServerUrl,
        "/mcp",
        supportsElicitation = true
      )
    }

    @Test
    fun `test nickname tool with accept elicitation action`(): Unit = runBlocking {
      val nickName = "Test Man"

      mcpClient.setElicitationHandler {
        CreateElicitationResult(
          action = CreateElicitationResult.Action.accept,
          content = GetNicknameRequest(nickName).encode(),
        )
      }

      val response = mcpClient.callNicknameTool()
      assertNotNull(response)
      val content = response.content.first() as? TextContent
      assertNotNull(content)
      assertEquals("Hello, $nickName!", content.text)
    }

    @Test
    fun `test nickname tool with decline elicitation action`(): Unit = runBlocking {
      mcpClient.setElicitationHandler {
        CreateElicitationResult(
          action = CreateElicitationResult.Action.decline,
        )
      }

      val response = mcpClient.callNicknameTool()
      assertNotNull(response)
      val content = response.content.first() as? TextContent
      assertNotNull(content)
      assertEquals("Sorry, don't know what to call you", content.text)
    }

    @Test
    fun `test nickname tool with cancel elicitation action`(): Unit = runBlocking {
      mcpClient.setElicitationHandler {
        CreateElicitationResult(
          action = CreateElicitationResult.Action.cancel,
        )
      }

      val response = mcpClient.callNicknameTool()
      assertNotNull(response)
      val content = response.content.first() as? TextContent
      assertNotNull(content)
      assertEquals("Lets try again!", content.text)
    }
  }


  @Inject
  lateinit var jettyService: JettyService
  @Inject
  private lateinit var registry: CollectorRegistry

  protected val mcpServerTestConfig = McpConfig(
    buildMap {
      put(
        SERVER_NAME,
        McpServerConfig(
          version = "1.0.0",
        ),
      )
    },
  )

  val mcpServerTestModule = object : KAbstractModule() {
    override fun configure() {
      install(McpServerModule.create(SERVER_NAME, mcpServerTestConfig))

      install(McpToolModule.create<CalculatorTool>())
      install(McpToolModule.create<KotlinSdkTool>())
      install(McpToolModule.create<ThrowingTool>())
      install(McpToolModule.create<HierarchicalTool>())
      install(McpToolModule.create<NicknameElicitationTool>())
      install(McpPromptModule.create<KotlinDeveloperPrompt>())
      install(McpResourceModule.create<WebSearchResource>())

      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
    }
  }

  lateinit var mcpClient: Client

  @Test
  fun `test ListTools`() = runBlocking {
    val request = ListToolsRequest()
    val response = mcpClient.listTools(request)
    assertEquals(
      expected = 5,
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

    // Check kotlin-sdk-tool meta
    assertEquals("1.2.3", kotlinSdkTool._meta.decode<VersionMetadata>().version)
  }

  @Test
  fun `test add two integers`() = runBlocking {
    val response = mcpClient.callCalculatorTool(5, 3, Operation.ADD)
    assertNotNull(response)
    val structuredResult = assertNotNull(response.structuredContent).decode<CalculatorToolOutput>()

    assertEquals(
      expected = 8,
      actual = structuredResult.result,
      message = "Expected the calculator tool to return the correct addition result",
    )
  }

  @Test
  fun `test ListPrompts`() = runBlocking {
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

    val textContent = message.content as? TextContent
    assertNotNull(textContent)
    assertContains(
      textContent.text!!,
      "Develop a kotlin project named <name>TestProject</name>",
      message = "Expected the project name to be included in the prompt message",
    )
  }

  @Test
  fun `test ListResources`() = runBlocking {
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
    mcpClient.callCalculatorTool(4, 2, Operation.DIVIDE)

    assertEquals(
      expected = 1.0,
      actual = registry.summaryCount(
        "mcp_tool_handler_latency",
        "server_name" to "mcp-server-action-test-server",
        "tool_name" to "calculator",
        "tool_outcome" to "Success",
      )
    )
  }

  @Test
  fun `test error metrics`(): Unit = runBlocking {

    // Divide by zero
    mcpClient.callCalculatorTool(4, 0, Operation.DIVIDE)

    assertEquals(
      expected = 1.0,
      actual =
        registry.summaryCount(
        "mcp_tool_handler_latency",
        "server_name" to "mcp-server-action-test-server",
        "tool_name" to "calculator",
        "tool_outcome" to "Error",
      )
    )
  }

  @Test
  fun `test exception metrics`(): Unit = runBlocking {
    val result = mcpClient.callThrowingTool()
    assertEquals(true, result?.isError)

    assertEquals(
      expected = 1.0,
      actual = registry.summaryCount(
        "mcp_tool_handler_latency",
        "server_name" to "mcp-server-action-test-server",
        "tool_name" to "throwing",
        "tool_outcome" to "Exception",
      )
    )
  }

  @Test
  fun `test hierarchical tool`(): Unit = runBlocking {
    val response = mcpClient.callHierarchicalTool()
    assertNotNull(response)

    val structuredResult = assertNotNull(response.structuredContent).decode<HierarchicalToolOutput>()

    assertThat(structuredResult).isEqualTo(HierarchicalToolOutput("test"))
  }

  companion object {
    private const val SERVER_NAME = "mcp-server-action-test-server"
  }
}
