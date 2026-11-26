@file:OptIn(ExperimentalMiskApi::class)

package misk.mcp

import com.google.inject.Module
import jakarta.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import misk.annotation.ExperimentalMiskApi
import misk.config.MiskConfig
import misk.crypto.CryptoConfig
import misk.crypto.Key
import misk.crypto.KeyType
import misk.crypto.testing.CryptoTestModule
import misk.inject.KAbstractModule
import misk.mcp.McpProtocolTest.Companion.mcpTestModule
import misk.mcp.config.McpServerConfig
import misk.mcp.protocol.ClientCapabilities
import misk.mcp.protocol.CompleteRequest
import misk.mcp.protocol.CompleteRequestParams
import misk.mcp.protocol.ElicitResult
import misk.mcp.protocol.LATEST_LEGACY_PROTOCOL_VERSION
import misk.mcp.protocol.McpException
import misk.mcp.protocol.McpMethod
import misk.mcp.protocol.PromptReference
import misk.mcp.protocol.RPCError
import misk.mcp.protocol.TextContent
import misk.mcp.testing.McpTestClient
import misk.mcp.testing.prompts.KotlinDeveloperPrompt
import misk.mcp.testing.tools.GetNicknameRequest
import misk.mcp.testing.tools.NicknameElicitationTool
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class McpInputRequiredTest {
  @MiskTestModule
  val module: Module =
    object : KAbstractModule() {
      override fun configure() {
        install(
          mcpTestModule(
            McpToolModule.create<NicknameElicitationTool>(),
            McpToolModule.create<CatchAllTool>(),
            McpPromptModule.create<KotlinDeveloperPrompt>(),
            McpCompletionModule.create<InputRequiringCompletions>(),
            config = McpServerConfig("1.0.0", request_state_key = KEY_NAME),
          )
        )
        install(
          CryptoTestModule(
            CryptoConfig(listOf(Key(KEY_NAME, KeyType.MAC, MiskConfig.RealSecret(""))), "aws-kms://test")
          )
        )
      }
    }

  @Inject lateinit var jettyService: JettyService
  @Inject lateinit var clock: FakeClock

  private val url by lazy { jettyService.httpServerUrl.resolve("/mcp")!! }
  private val formCapable = ClientCapabilities(elicitation = ClientCapabilities.Elicitation())
  private val rawClient by lazy { McpTestClient(OkHttpClient(), url, capabilities = formCapable) }

  @Test
  fun `the client's answer completes the tool call, with the state of the first round`() = runBlocking {
    val answers =
      mapOf(
        ElicitResult(ElicitResult.Action.Accept, buildJsonObject { put("nickname", "Ada") }) to "Hello, Ada!",
        ElicitResult(ElicitResult.Action.Decline) to "Sorry, don't know what to call you",
      )
    for ((answer, expected) in answers) {
      val client =
        McpTestClient(
          OkHttpClient(),
          url,
          capabilities = formCapable,
          inputHandler = { key, _ ->
            assertEquals("nickname", key)
            answer
          },
        )

      val result = client.callTool("nickname")

      assertEquals(expected, (result.content.single() as TextContent).text)
    }
  }

  @Test
  fun `a handler that catches the input request still ends with it`() = runBlocking {
    val result =
      rawClient.request(
        McpMethod.TOOLS_CALL,
        buildJsonObject {
          put("name", "catch-all")
          putJsonObject("arguments") {}
        },
      )

    assertEquals("input_required", result["resultType"]?.jsonPrimitive?.content, result.toString())
  }

  @Test
  fun `clients without the elicitation capability get -32021`() = runBlocking {
    val client = McpTestClient(OkHttpClient(), url)

    val error = assertFailsWith<McpException> { client.callTool("nickname") }

    assertEquals(RPCError.ErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY, error.code)
    assertEquals(
      buildJsonObject {
        putJsonObject("requiredCapabilities") { putJsonObject("elicitation") { putJsonObject("form") {} } }
      },
      error.data,
    )
  }

  @Test
  fun `legacy clients get an error result`() = runBlocking {
    val client = McpTestClient(OkHttpClient(), url, LATEST_LEGACY_PROTOCOL_VERSION, capabilities = formCapable)

    val result = client.callTool("nickname")

    assertEquals(true, result.isError)
  }

  @Test
  fun `a tampered, expired or misplaced request state is invalid`() = runBlocking {
    val state = firstRound()["requestState"]!!.jsonPrimitive.content
    val (payload, tag) = state.split('.')

    assertInvalidState(retry("${payload}x.$tag"))
    assertInvalidState(retry(state, arguments = buildJsonObject { put("other", 1) }))
    clock.add(11.minutes.toJavaDuration())
    assertInvalidState(retry(state))
  }

  @Test
  fun `only tools, prompts and resources can require input`() = runBlocking {
    val error =
      assertFailsWith<McpException> {
        rawClient.complete(
          CompleteRequest(
            CompleteRequestParams(
              argument = CompleteRequestParams.Argument("Project Name", ""),
              ref = PromptReference("Kotlin Developer"),
            )
          )
        )
      }

    assertEquals(RPCError.ErrorCode.INTERNAL_ERROR, error.code)
  }

  private suspend fun firstRound(): JsonObject {
    val result =
      rawClient.request(
        McpMethod.TOOLS_CALL,
        buildJsonObject {
          put("name", "nickname")
          putJsonObject("arguments") {}
        },
      )
    assertEquals("input_required", result["resultType"]?.jsonPrimitive?.content, result.toString())
    return result
  }

  private suspend fun retry(state: String, arguments: JsonObject = JsonObject(emptyMap())) = runCatching {
    rawClient.request(
      McpMethod.TOOLS_CALL,
      buildJsonObject {
        put("name", "nickname")
        put("arguments", arguments)
        putJsonObject("inputResponses") {
          putJsonObject("nickname") {
            put("action", "accept")
            putJsonObject("content") { put("nickname", "Ada") }
          }
        }
        put("requestState", JsonPrimitive(state))
      },
    )
  }

  private fun assertInvalidState(result: Result<JsonObject>) {
    val error = result.exceptionOrNull() as McpException
    assertEquals(RPCError.ErrorCode.INVALID_PARAMS, error.code)
    assert(error.message!!.startsWith("requestState")) { error.message!! }
  }

  class CatchAllTool @Inject constructor() : McpToolEmptyInput() {
    override val name = "catch-all"
    override val description = "Wraps its body in a catch-all"

    override suspend fun handle(): ToolResult =
      try {
        inputRequired { elicit<GetNicknameRequest>("nickname", "?") }
      } catch (e: Exception) {
        ToolResult(TextContent("swallowed"), isError = true)
      }
  }

  class InputRequiringCompletions @Inject constructor() : McpCompletionProvider {
    override val ref = PromptReference("Kotlin Developer")

    override suspend fun complete(request: CompleteRequest): List<String> = inputRequired {
      elicit<GetNicknameRequest>("x", "?")
    }
  }

  private companion object {
    const val KEY_NAME = "mcp-request-state"
  }
}
