@file:OptIn(ExperimentalMiskApi::class)

package misk.mcp

import com.google.inject.Module
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import misk.MiskTestingServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.mcp.action.McpPost
import misk.mcp.action.McpStreamManager
import misk.mcp.action.handleMessage
import misk.mcp.config.McpConfig
import misk.mcp.config.McpServerConfig
import misk.mcp.protocol.CallToolResult
import misk.mcp.protocol.JSONRPCMessage
import misk.mcp.protocol.JSONRPCRequest
import misk.mcp.protocol.JSONRPCResponse
import misk.mcp.protocol.McpMethod
import misk.mcp.protocol.RequestId
import misk.mcp.protocol.TextContent
import misk.mcp.testing.McpTestClient
import misk.scope.ActionScoped
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.HttpCall
import misk.web.RequestBody
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.sse.ServerSentEvent
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test

/** Handlers must run inline on the web action's coroutine. Moving them to a detached scope breaks these tests. */
@MiskTest(startService = true)
internal class McpInlineDispatchTest {
  @MiskTestModule
  val module: Module =
    object : KAbstractModule() {
      override fun configure() {
        install(McpServerModule.create("inline", McpConfig(mapOf("inline" to McpServerConfig(version = "1.0.0")))))
        install(McpToolModule.create<ContextTool>())
        install(WebActionModule.create<ContextAction>())
        install(WebServerTestingModule())
        install(MiskTestingServiceModule())
      }
    }

  @Inject lateinit var jettyService: JettyService
  @Inject lateinit var recorder: Recorder

  @Test
  fun `tools see the action's scope and context, and handleMessage waits for them`() = runBlocking {
    val client = McpTestClient(OkHttpClient(), jettyService.httpServerUrl.resolve("/mcp")!!)
    val request =
      JSONRPCRequest(
        id = RequestId(1),
        method = McpMethod.TOOLS_CALL,
        params =
          buildJsonObject {
            put("name", "context")
            putJsonObject("arguments") {}
          },
      )

    val response = client.send(request, mapOf("X-Caller" to "caller"))

    val result = McpJson.decodeFromJsonElement(CallToolResult.serializer(), (response.reply as JSONRPCResponse).result)
    assertEquals("caller/from-action", (result.content.single() as TextContent).text)
    assertEquals(true, recorder.toolFinishedBeforeReturn)
  }

  data class ActionElement(val value: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ActionElement>
  }

  @Singleton
  class Recorder @Inject constructor() {
    @Volatile var toolFinished = false
    @Volatile var toolFinishedBeforeReturn: Boolean? = null
  }

  class ContextTool @Inject constructor(private val httpCall: ActionScoped<HttpCall>, private val recorder: Recorder) :
    McpToolEmptyInput() {
    override val name = "context"
    override val description = "Reports the caller header and the action's coroutine context element"

    override suspend fun handle(): ToolResult {
      val caller = httpCall.get().requestHeaders["X-Caller"]
      val element = currentCoroutineContext()[ActionElement]?.value
      delay(100)
      recorder.toolFinished = true
      return ToolResult(TextContent("$caller/$element"))
    }
  }

  class ContextAction
  @Inject
  constructor(private val mcpStreamManager: McpStreamManager, private val recorder: Recorder) : WebAction {
    @McpPost
    suspend fun post(@RequestBody message: JSONRPCMessage, sendChannel: SendChannel<ServerSentEvent>) {
      withContext(ActionElement("from-action")) {
        mcpStreamManager.withSseChannel(sendChannel) { handleMessage(message) }
      }
      recorder.toolFinishedBeforeReturn = recorder.toolFinished
    }
  }
}
