@file:OptIn(ExperimentalMiskApi::class)

package misk.mcp

import com.google.inject.Module
import jakarta.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import misk.MiskTestingServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.mcp.config.McpConfig
import misk.mcp.config.McpServerConfig
import misk.mcp.protocol.JSONRPCError
import misk.mcp.protocol.JSONRPCNotification
import misk.mcp.protocol.JSONRPCRequest
import misk.mcp.protocol.JSONRPCResponse
import misk.mcp.protocol.LATEST_LEGACY_PROTOCOL_VERSION
import misk.mcp.protocol.McpException
import misk.mcp.protocol.McpMetaKeys
import misk.mcp.protocol.McpMethod
import misk.mcp.protocol.RPCError
import misk.mcp.protocol.RequestId
import misk.mcp.protocol.TextContent
import misk.mcp.testing.McpTestClient
import misk.mcp.testing.prompts.KotlinDeveloperPrompt
import misk.mcp.testing.resources.UserProfileResource
import misk.mcp.testing.resources.WebSearchResource
import misk.mcp.testing.tools.CalculatorTool
import misk.mcp.testing.tools.ThrowingTool
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class McpProtocolTest {
  @MiskTestModule
  val module: Module =
    mcpTestModule(
      McpToolModule.create<ThrowingTool>(),
      McpToolModule.create<CalculatorTool>(),
      McpPromptModule.create<KotlinDeveloperPrompt>(),
      McpResourceModule.create<WebSearchResource>(),
      McpResourceTemplateModule.create<UserProfileResource>(),
    )

  @Inject lateinit var jettyService: JettyService

  private val modern by lazy { McpTestClient(OkHttpClient(), jettyService.httpServerUrl.resolve("/mcp")!!) }
  private val legacy by lazy {
    McpTestClient(OkHttpClient(), jettyService.httpServerUrl.resolve("/mcp")!!, LATEST_LEGACY_PROTOCOL_VERSION)
  }

  @Test
  fun `server discover returns versions, capabilities and server info`() = runBlocking {
    val result = modern.request(McpMethod.SERVER_DISCOVER, null)

    assertEquals(listOf("2026-07-28"), result["supportedVersions"]!!.jsonArray.map { it.jsonPrimitive.content })
    assertThat(result["capabilities"]!!.jsonObject.keys).containsExactlyInAnyOrder("tools", "prompts", "resources")
    assertEquals(SERVER_NAME, result.serverInfoName())
    assertModernCacheHints(result)
  }

  @Test
  fun `the test client reads server info in both eras`() = runBlocking {
    assertEquals(SERVER_NAME, modern.serverInfo().name)
    assertEquals(SERVER_NAME, legacy.serverInfo().name)
  }

  @Test
  fun `modern list and read results carry the result type, server info and cache hints`() = runBlocking {
    val results =
      listOf(
        modern.request(McpMethod.TOOLS_LIST, null),
        modern.request(McpMethod.PROMPTS_LIST, null),
        modern.request(McpMethod.RESOURCES_LIST, null),
        modern.request(McpMethod.RESOURCES_TEMPLATES_LIST, null),
        modern.request(McpMethod.RESOURCES_READ, buildJsonObject { put("uri", "https://search.com/") }),
      )
    results.forEach { assertModernCacheHints(it) }

    val call = modern.request(McpMethod.TOOLS_CALL, calculatorCall())
    assertEquals("complete", call["resultType"]?.jsonPrimitive?.content)
    assertEquals(SERVER_NAME, call.serverInfoName())
    assertNull(call["ttlMs"])
  }

  @Test
  fun `legacy results have no modern fields`() = runBlocking {
    val result = legacy.request(McpMethod.TOOLS_LIST, null)

    assertNull(result["resultType"])
    assertNull(result["ttlMs"])
    assertNull(result["_meta"])
  }

  @Test
  fun `modern requests without required meta are invalid`() = runBlocking {
    val noMeta = modern.send(JSONRPCRequest(id = RequestId(1), method = McpMethod.SERVER_DISCOVER))
    assertEquals(RPCError.ErrorCode.INVALID_PARAMS, (noMeta.reply as JSONRPCError).error.code)

    val noCapabilities =
      modern.send(
        JSONRPCRequest(
          id = RequestId(2),
          method = McpMethod.TOOLS_LIST,
          params = buildJsonObject { putJsonObject("_meta") { put(McpMetaKeys.PROTOCOL_VERSION, "2026-07-28") } },
        )
      )
    assertEquals(RPCError.ErrorCode.INVALID_PARAMS, (noCapabilities.reply as JSONRPCError).error.code)
  }

  @Test
  fun `unsupported versions list the supported ones`() = runBlocking {
    val client = McpTestClient(OkHttpClient(), jettyService.httpServerUrl.resolve("/mcp")!!, "1900-01-01")
    val error = assertFailsWith<McpException> { client.listTools() }

    assertEquals(RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION, error.code)
    assertEquals(
      buildJsonObject {
        put("supported", JsonArray(listOf(JsonPrimitive("2026-07-28"))))
        put("requested", "1900-01-01")
      },
      error.data,
    )
  }

  @Test
  fun `unknown methods are not found in both eras`() = runBlocking {
    for (client in listOf(modern, legacy)) {
      val error = assertFailsWith<McpException> { client.request("tools/unknown", null) }
      assertEquals(RPCError.ErrorCode.METHOD_NOT_FOUND, error.code)
    }
    val modernPing = assertFailsWith<McpException> { modern.ping() }
    assertEquals(RPCError.ErrorCode.METHOD_NOT_FOUND, modernPing.code)
  }

  @Test
  fun `legacy initialize negotiates the protocol version`() = runBlocking {
    assertEquals("2025-06-18", initialize("2025-06-18")["protocolVersion"]?.jsonPrimitive?.content)
    assertEquals(LATEST_LEGACY_PROTOCOL_VERSION, initialize("2026-07-28")["protocolVersion"]?.jsonPrimitive?.content)
  }

  @Test
  fun `legacy notifications are accepted and ping responds`() = runBlocking {
    val accepted = legacy.send(JSONRPCNotification(method = McpMethod.NOTIFICATIONS_INITIALIZED))
    assertEquals(202, accepted.code)
    assertEquals("", accepted.body)

    legacy.ping()
  }

  @Test
  fun `unknown tools and prompts and missing prompt arguments are invalid params`() = runBlocking {
    for (client in listOf(modern, legacy)) {
      val unknownTool = assertFailsWith<McpException> { client.callTool("missing") }
      assertEquals(RPCError.ErrorCode.INVALID_PARAMS, unknownTool.code)

      val unknownPrompt =
        assertFailsWith<McpException> { client.request(McpMethod.PROMPTS_GET, buildJsonObject { put("name", "x") }) }
      assertEquals(RPCError.ErrorCode.INVALID_PARAMS, unknownPrompt.code)

      val missingArgument =
        assertFailsWith<McpException> {
          client.request(McpMethod.PROMPTS_GET, buildJsonObject { put("name", "Kotlin Developer") })
        }
      assertEquals(RPCError.ErrorCode.INVALID_PARAMS, missingArgument.code)
    }
  }

  @Test
  fun `missing resources use the era's error code`() = runBlocking {
    val params = buildJsonObject { put("uri", "unknown://thing") }

    val modernError = assertFailsWith<McpException> { modern.request(McpMethod.RESOURCES_READ, params) }
    assertEquals(RPCError.ErrorCode.INVALID_PARAMS, modernError.code)
    assertEquals(params, modernError.data)

    val legacyError = assertFailsWith<McpException> { legacy.request(McpMethod.RESOURCES_READ, params) }
    assertEquals(RPCError.ErrorCode.RESOURCE_NOT_FOUND, legacyError.code)
    assertEquals(params, legacyError.data)
  }

  @Test
  fun `tools are listed by name`() = runBlocking {
    assertEquals(listOf("calculator", "throwing"), modern.listTools().tools.map { it.name })
  }

  @Test
  fun `tool exceptions become error results`() = runBlocking {
    val result = modern.callTool("throwing", mapOf("dummy" to "x"))

    assertEquals(true, result.isError)
    assertEquals("Error executing tool throwing: boom!", (result.content.single() as TextContent).text)
  }

  private suspend fun initialize(version: String): JsonObject {
    val response =
      legacy.send(
        JSONRPCRequest(
          id = RequestId(1),
          method = McpMethod.INITIALIZE,
          params =
            buildJsonObject {
              put("protocolVersion", version)
              putJsonObject("capabilities") {}
              putJsonObject("clientInfo") {
                put("name", "test")
                put("version", "1")
              }
            },
        )
      )
    return (response.reply as JSONRPCResponse).result
  }

  private fun calculatorCall() = buildJsonObject {
    put("name", "calculator")
    putJsonObject("arguments") {
      put("operation", "ADD")
      put("first_term", 1)
      put("second_term", 2)
    }
  }

  private fun assertModernCacheHints(result: JsonObject) {
    assertEquals("complete", result["resultType"]?.jsonPrimitive?.content)
    assertEquals(SERVER_NAME, result.serverInfoName())
    assertEquals(0, result["ttlMs"]?.jsonPrimitive?.content?.toInt())
    assertEquals("private", result["cacheScope"]?.jsonPrimitive?.content)
  }

  private fun JsonObject.serverInfoName() =
    this["_meta"]?.jsonObject?.get(McpMetaKeys.SERVER_INFO)?.jsonObject?.get("name")?.jsonPrimitive?.content

  @MiskTest(startService = true)
  class CapabilityGatingTest {
    @MiskTestModule val module: Module = mcpTestModule(McpToolModule.create<CalculatorTool>())

    @Inject lateinit var jettyService: JettyService

    @Test
    fun `methods of unadvertised capabilities are not found`() = runBlocking {
      val url = jettyService.httpServerUrl.resolve("/mcp")!!
      val modern = McpTestClient(OkHttpClient(), url)

      assertThat(modern.discover().capabilities.prompts).isNull()
      for (client in listOf(modern, McpTestClient(OkHttpClient(), url, LATEST_LEGACY_PROTOCOL_VERSION))) {
        assertEquals(RPCError.ErrorCode.METHOD_NOT_FOUND, assertFailsWith<McpException> { client.listPrompts() }.code)
        assertEquals(RPCError.ErrorCode.METHOD_NOT_FOUND, assertFailsWith<McpException> { client.listResources() }.code)
      }
    }
  }

  companion object {
    const val SERVER_NAME = "mcp-protocol-test"

    fun mcpTestModule(vararg features: Module, config: McpServerConfig = McpServerConfig("1.0.0")): Module =
      object : KAbstractModule() {
        override fun configure() {
          // Before McpServerModule, so the tests also check that the JSON unmarshaller misk installs decodes MCP
          // messages
          install(WebServerTestingModule())
          install(MiskTestingServiceModule())
          install(McpServerModule.create(SERVER_NAME, McpConfig(mapOf(SERVER_NAME to config))))
          features.forEach { install(it) }
          install(WebActionModule.create<McpServerActionTestPostAction>())
        }
      }
  }
}
