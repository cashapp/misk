@file:OptIn(ExperimentalMiskApi::class)

package misk.mcp

import com.google.inject.Module
import com.google.inject.util.Modules
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListPromptsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import misk.MiskTestingServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.mcp.action.McpPost
import misk.mcp.action.McpStreamManager
import misk.mcp.action.McpWebSocket
import misk.mcp.action.currentClientConnection
import misk.mcp.action.currentServerSession
import misk.mcp.action.handleMessage
import misk.mcp.config.McpConfig
import misk.mcp.config.McpServerConfig
import misk.mcp.testing.asMcpStreamableHttpClient
import misk.mcp.testing.asMcpWebSocketClient
import misk.mcp.testing.prompts.KotlinDeveloperPrompt
import misk.mcp.testing.resources.UserProfileResource
import misk.mcp.testing.resources.WebSearchResource
import misk.mcp.testing.tools.CalculatorTool
import misk.mcp.testing.tools.CalculatorToolInput.Operation
import misk.mcp.testing.tools.CalculatorToolOutput
import misk.mcp.testing.tools.ClientConnectionTool
import misk.mcp.testing.tools.ClientConnectionToolOutput
import misk.mcp.testing.tools.GetNicknameRequest
import misk.mcp.testing.tools.HelloWorldTool
import misk.mcp.testing.tools.HelloWorldToolOutput
import misk.mcp.testing.tools.HierarchicalTool
import misk.mcp.testing.tools.HierarchicalToolOutput
import misk.mcp.testing.tools.KotlinSdkTool
import misk.mcp.testing.tools.NicknameElicitationTool
import misk.mcp.testing.tools.ThrowingTool
import misk.mcp.testing.tools.VersionMetadata
import misk.mcp.testing.tools.callCalculatorTool
import misk.mcp.testing.tools.callClientConnectionTool
import misk.mcp.testing.tools.callHelloWorld
import misk.mcp.testing.tools.callHierarchicalTool
import misk.mcp.testing.tools.callNicknameTool
import misk.mcp.testing.tools.callThrowingTool
import misk.metrics.summaryCount
import misk.scope.ActionScoped
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.HttpCall
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

internal abstract class McpServerActionTest {

  @MiskTest(startService = true)
  class McpStreamableHttpServerActionTest : McpServerActionTest() {
    @OptIn(ExperimentalMiskApi::class)
    @Suppress("unused")
    class McpServerActionTestPostAction
    @Inject
    constructor(private val mcpStreamManager: McpStreamManager, private val probe: InlineDispatchProbe) : WebAction {
      @McpPost
      suspend fun mcpPost(@RequestBody message: JSONRPCMessage, sendChannel: SendChannel<ServerSentEvent>) {
        withContext(CallerContextElement(CALLER_CONTEXT_VALUE)) {
          mcpStreamManager.withSseChannel(sendChannel) {
            handleMessage(message)
            if (message is JSONRPCRequest && message.method == Method.Defined.ToolsCall.value) {
              probe.handlerCompletedWhenHandleMessageReturned.complete(probe.handlerCompleted.get())
            }
          }
        }
      }
    }

    @Suppress("unused")
    @MiskTestModule
    val module: Module = Modules.combine(mcpServerTestModule, WebActionModule.create<McpServerActionTestPostAction>())

    @BeforeEach
    fun setUp() = runBlocking {
      mcpClient =
        OkHttpClient().asMcpStreamableHttpClient(jettyService.httpServerUrl, "/mcp", supportsElicitation = true)
    }

    @Inject private lateinit var probe: InlineDispatchProbe

    @Test
    fun `tool handler inherits the web action's coroutine context`() = runBlocking {
      val response = mcpClient.callTool(name = "inline_dispatch", arguments = emptyMap())

      val result = assertNotNull(response.structuredContent).decode<InlineDispatchToolOutput>()
      assertEquals(CALLER_CONTEXT_VALUE, result.callerContextValue)
      assertEquals("/mcp", result.requestPath)
    }

    @Test
    fun `handleMessage returns only after the tool handler completes`() = runBlocking {
      mcpClient.callTool(name = "inline_dispatch", arguments = emptyMap())

      assertTrue(withTimeout(5.seconds) { probe.handlerCompletedWhenHandleMessageReturned.await() })
    }
  }

  @MiskTest(startService = true)
  class McpWebSocketServerActionTest : McpServerActionTest() {
    @OptIn(ExperimentalMiskApi::class)
    @Suppress("unused")
    class McpWebSocketServerActionTestAction @Inject constructor(private val mcpStreamManager: McpStreamManager) :
      WebAction {
      @McpWebSocket fun handle(webSocket: WebSocket) = mcpStreamManager.withWebSocket(webSocket)
    }

    @Suppress("unused")
    @MiskTestModule
    val module: Module =
      Modules.combine(mcpServerTestModule, WebActionModule.create<McpWebSocketServerActionTestAction>())

    @BeforeEach
    fun setUp() = runBlocking {
      mcpClient = OkHttpClient().asMcpWebSocketClient(jettyService.httpServerUrl, "/mcp", supportsElicitation = true)
    }

    @Test
    fun `test nickname tool with accept elicitation action`(): Unit = runBlocking {
      val nickName = "Test Man"

      mcpClient.setElicitationHandler {
        ElicitResult(action = ElicitResult.Action.Accept, content = GetNicknameRequest(nickName).encode())
      }

      val response = mcpClient.callNicknameTool()
      assertNotNull(response)
      val content = response.content.first() as? TextContent
      assertNotNull(content)
      assertEquals("Hello, $nickName!", content.text)
    }

    @Test
    fun `test nickname tool with decline elicitation action`(): Unit = runBlocking {
      mcpClient.setElicitationHandler { ElicitResult(action = ElicitResult.Action.Decline) }

      val response = mcpClient.callNicknameTool()
      assertNotNull(response)
      val content = response.content.first() as? TextContent
      assertNotNull(content)
      assertEquals("Sorry, don't know what to call you", content.text)
    }

    @Test
    fun `test nickname tool with cancel elicitation action`(): Unit = runBlocking {
      mcpClient.setElicitationHandler { ElicitResult(action = ElicitResult.Action.Cancel) }

      val response = mcpClient.callNicknameTool()
      assertNotNull(response)
      val content = response.content.first() as? TextContent
      assertNotNull(content)
      assertEquals("Lets try again!", content.text)
    }
  }

  @Inject lateinit var jettyService: JettyService
  @Inject private lateinit var registry: CollectorRegistry

  protected val mcpServerTestConfig = McpConfig(buildMap { put(SERVER_NAME, McpServerConfig(version = "1.0.0")) })

  val mcpServerTestModule =
    object : KAbstractModule() {
      override fun configure() {
        install(McpServerModule.create(SERVER_NAME, mcpServerTestConfig))

        install(McpToolModule.create<CalculatorTool>())
        install(McpToolModule.create<KotlinSdkTool>())
        install(McpToolModule.create<ThrowingTool>())
        install(McpToolModule.create<HierarchicalTool>())
        install(McpToolModule.create<NicknameElicitationTool>())
        install(McpToolModule.create<HelloWorldTool>())
        install(McpToolModule.create<ClientConnectionTool>())
        install(McpToolModule.create<InlineDispatchTool>())
        install(McpToolModule.create<ServerSessionTool>())
        install(McpPromptModule.create<KotlinDeveloperPrompt>())
        install(McpResourceModule.create<WebSearchResource>())
        install(McpResourceTemplateModule.create<UserProfileResource>())

        install(WebServerTestingModule())
        install(MiskTestingServiceModule())
      }
    }

  lateinit var mcpClient: Client

  @Test
  fun `test ListTools`() = runBlocking {
    val request = ListToolsRequest()
    val response = mcpClient.listTools(request)
    assertEquals(expected = 9, actual = response.tools.size, message = "Expecting nine tools to be registered")

    // Check calculator tool
    val calculatorTool = response.tools.find { it.name == "calculator" }
    assertNotNull(calculatorTool)
    assertEquals(
      expected = "calculator",
      actual = calculatorTool.name,
      message = "Expected the calculator tool name to be 'calculator'",
    )
    assertEquals(
      expected =
        """
        |A simple calculator that can perform basic arithmetic operations such as 
        |addition, subtraction, multiplication, and division on two integers. In the input, 
        |the operation will be interpreted as `firstTerm <operation> secondTerm`. 
        |"""
          .trimMargin(),
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
    assertEquals("1.2.3", kotlinSdkTool.meta?.decode<VersionMetadata>()?.version)

    // Check HelloWorldTool
    val helloWorldTool = response.tools.find { it.name == "HelloWorldTool" }
    assertNotNull(helloWorldTool)
    assertNotNull(helloWorldTool.inputSchema)
    assertEquals(
      expected = "object",
      actual = helloWorldTool.inputSchema.type,
      message = "Expected input schema type to be 'object'",
    )
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
  fun `test hello world tool`() = runBlocking {
    val response = mcpClient.callHelloWorld()
    assertNotNull(response)
    val structuredResult = assertNotNull(response.structuredContent).decode<HelloWorldToolOutput>()

    assertEquals(
      expected = "Hello, world!",
      actual = structuredResult.greeting,
      message = "Expected the HelloWorld tool to return the correct greeting",
    )
  }

  @Test
  fun `test ListPrompts`() = runBlocking {
    val request = ListPromptsRequest()
    val response = mcpClient.listPrompts(request)

    assertEquals(expected = 1, actual = response.prompts.size, message = "Expecting one prompt to be registered")

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
    val request =
      GetPromptRequest(
        GetPromptRequestParams(name = "Kotlin Developer", arguments = mapOf("Project Name" to "TestProject"))
      )
    val response = mcpClient.getPrompt(request)

    assertNotNull(response)
    assertEquals(expected = "Description for Kotlin Developer", actual = response.description)

    assertEquals(expected = 1, actual = response.messages.size, message = "Expected one message in the prompt response")

    val message = response.messages.firstOrNull()
    assertNotNull(message)
    assertEquals("User", message.role.toString())

    val textContent = message.content as? TextContent
    assertNotNull(textContent)
    assertContains(
      textContent.text,
      "Develop a kotlin project named <name>TestProject</name>",
      message = "Expected the project name to be included in the prompt message",
    )
  }

  @Test
  fun `test ListResources`() = runBlocking {
    val request = ListResourcesRequest()
    val response = mcpClient.listResources(request)

    assertEquals(expected = 1, actual = response.resources.size, message = "Expecting one resource to be registered")

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
    val request = ReadResourceRequest(ReadResourceRequestParams(uri = "https://search.com/"))
    val response = mcpClient.readResource(request)

    assertNotNull(response)
    assertEquals(
      expected = 1,
      actual = response.contents.size,
      message = "Expected one content item in the resource response",
    )

    val content = response.contents.firstOrNull() as? TextResourceContents
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
  fun `test ListResourceTemplates`() = runBlocking {
    val request = ListResourceTemplatesRequest()
    val response = mcpClient.listResourceTemplates(request)

    assertEquals(
      expected = 1,
      actual = response.resourceTemplates.size,
      message = "Expecting one resource template to be registered",
    )

    val userProfileTemplate = response.resourceTemplates.firstOrNull()
    assertNotNull(userProfileTemplate)
    assertEquals(
      expected = "users://{userId}/profile",
      actual = userProfileTemplate.uriTemplate,
      message = "Expected the correct URI template",
    )
    assertEquals(
      expected = "User Profile",
      actual = userProfileTemplate.name,
      message = "Expected the correct name for the resource template",
    )
    assertEquals(
      expected = "Profile information for a specific user",
      actual = userProfileTemplate.description,
      message = "Expected the correct description for the resource template",
    )
  }

  @Test
  fun `test ReadResourceTemplate`() = runBlocking {
    val request = ReadResourceRequest(ReadResourceRequestParams(uri = "users://alice/profile"))
    val response = mcpClient.readResource(request)

    assertNotNull(response)
    assertEquals(
      expected = 1,
      actual = response.contents.size,
      message = "Expected one content item in the resource template response",
    )

    val content = response.contents.firstOrNull() as? TextResourceContents
    assertNotNull(content)
    assertEquals(
      expected = "users://alice/profile",
      actual = content.uri,
      message = "Expected the correct URI in the resource content",
    )
    assertEquals(
      expected = "application/json",
      actual = content.mimeType,
      message = "Expected the correct MIME type for the resource content",
    )
    assertContains(
      content.text,
      "\"userId\": \"alice\"",
      message = "Expected the userId variable to be extracted from the URI",
    )
  }

  @Test
  fun `test success metrics`(): Unit = runBlocking {
    mcpClient.callCalculatorTool(4, 2, Operation.DIVIDE)

    assertEquals(
      expected = 1.0,
      actual =
        registry.summaryCount(
          "mcp_tool_handler_latency",
          "server_name" to "mcp-server-action-test-server",
          "server_version" to "1.0.0",
          "tool_name" to "calculator",
          "tool_outcome" to "Success",
        ),
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
          "server_version" to "1.0.0",
          "tool_name" to "calculator",
          "tool_outcome" to "Error",
        ),
    )
  }

  @Test
  fun `test exception metrics`(): Unit = runBlocking {
    val result = mcpClient.callThrowingTool()
    assertEquals(true, result.isError)

    assertEquals(
      expected = 1.0,
      actual =
        registry.summaryCount(
          "mcp_tool_handler_latency",
          "server_name" to "mcp-server-action-test-server",
          "server_version" to "1.0.0",
          "tool_name" to "throwing",
          "tool_outcome" to "Exception",
        ),
    )
  }

  @Test
  fun `test hierarchical tool`(): Unit = runBlocking {
    val response = mcpClient.callHierarchicalTool()
    assertNotNull(response)

    val structuredResult = assertNotNull(response.structuredContent).decode<HierarchicalToolOutput>()

    assertThat(structuredResult).isEqualTo(HierarchicalToolOutput("test"))
  }

  @Test
  fun `test currentClientConnection is accessible in tool handler`() = runBlocking {
    val response = mcpClient.callClientConnectionTool()
    assertNotNull(response)

    val result = assertNotNull(response.structuredContent).decode<ClientConnectionToolOutput>()
    assertTrue(result.sessionId.isNotBlank(), "Expected a non-blank session ID from ClientConnection")
  }

  @Test
  fun `test currentServerSession is the handler's session`() = runBlocking {
    val response = mcpClient.callTool(name = "server_session", arguments = emptyMap())

    val result = assertNotNull(response.structuredContent).decode<ServerSessionToolOutput>()
    assertEquals(result.clientConnectionSessionId, result.serverSessionId)
  }

  companion object {
    private const val SERVER_NAME = "mcp-server-action-test-server"
    private const val CALLER_CONTEXT_VALUE = "installed-by-web-action"
  }
}

@Serializable
internal data class ServerSessionToolOutput(val serverSessionId: String, val clientConnectionSessionId: String)

internal class ServerSessionTool @Inject constructor() : StructuredMcpToolEmptyInput<ServerSessionToolOutput>() {
  override val name = "server_session"
  override val description = "Returns the session IDs of the current ServerSession and ClientConnection"

  override suspend fun handle(): ToolResult =
    ToolResult(
      ServerSessionToolOutput(
        serverSessionId = currentServerSession().sessionId,
        clientConnectionSessionId = currentClientConnection().sessionId,
      )
    )
}

/** Coroutine context element a web action installs around [McpStreamManager.withSseChannel]. */
internal data class CallerContextElement(val value: String) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<CallerContextElement>
}

@Singleton
internal class InlineDispatchProbe @Inject constructor() {
  val handlerCompleted = AtomicBoolean(false)
  val handlerCompletedWhenHandleMessageReturned = CompletableDeferred<Boolean>()
}

@Serializable internal data class InlineDispatchToolOutput(val callerContextValue: String, val requestPath: String)

/** Reports web action state that is only visible when the SDK dispatches the handler inline on the caller. */
internal class InlineDispatchTool
@Inject
constructor(private val httpCall: ActionScoped<HttpCall>, private val probe: InlineDispatchProbe) :
  StructuredMcpToolEmptyInput<InlineDispatchToolOutput>() {
  override val name = "inline_dispatch"
  override val description = "Reports web action context visible to the tool handler"

  override suspend fun handle(): ToolResult {
    val output =
      InlineDispatchToolOutput(
        callerContextValue = currentCoroutineContext()[CallerContextElement]?.value.orEmpty(),
        requestPath = httpCall.get().url.encodedPath,
      )
    // Keeps an asynchronously dispatched handler running past handleMessage's return so the ordering check is reliable.
    delay(100.milliseconds)
    probe.handlerCompleted.set(true)
    return ToolResult(output)
  }
}
