@file:OptIn(ExperimentalMiskApi::class)

package misk.mcp

import com.google.inject.Module
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import jakarta.inject.Inject
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import misk.MiskTestingServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.mcp.config.McpConfig
import misk.mcp.config.McpServerConfig
import misk.mcp.testing.prompts.KotlinDeveloperPrompt
import misk.mcp.testing.resources.UserProfileResource
import misk.mcp.testing.tools.CalculatorTool
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** A legacy-era client, kotlin-sdk's, works against the native server. */
@MiskTest(startService = true)
internal class KotlinSdkClientInteropTest {
  @MiskTestModule
  val module: Module =
    object : KAbstractModule() {
      override fun configure() {
        install(McpServerModule.create("interop", McpConfig(mapOf("interop" to McpServerConfig(version = "1.0.0")))))
        install(McpToolModule.create<CalculatorTool>())
        install(McpPromptModule.create<KotlinDeveloperPrompt>())
        install(McpResourceTemplateModule.create<UserProfileResource>())
        install(WebActionModule.create<McpServerActionTestPostAction>())
        install(WebServerTestingModule())
        install(MiskTestingServiceModule())
      }
    }

  @Inject lateinit var jettyService: JettyService

  @Test
  fun `kotlin-sdk client lists and calls tools, reads resources and gets prompts`() = runBlocking {
    val httpClient =
      HttpClient(OkHttp) {
        engine { preconfigured = OkHttpClient.Builder().protocols(listOf(Protocol.HTTP_1_1)).build() }
        install(SSE)
      }
    val client = Client(Implementation(name = "kotlin-sdk", version = "1.0"))
    client.connect(StreamableHttpClientTransport(httpClient, jettyService.httpServerUrl.resolve("/mcp").toString()))

    assertEquals("interop", client.serverVersion?.name)
    assertThat(client.listTools().tools.map { it.name }).containsExactly("calculator")

    val sum =
      client.callTool(
        name = "calculator",
        arguments = mapOf("operation" to "ADD", "first_term" to 2, "second_term" to 3),
      )
    assertEquals(5, sum.structuredContent?.jsonObject?.get("result")?.jsonPrimitive?.int)

    val profile = client.readResource(ReadResourceRequest(ReadResourceRequestParams(uri = "users://alice/profile")))
    assertThat((profile.contents.single() as TextResourceContents).text).contains("alice")

    val prompt =
      client.getPrompt(
        GetPromptRequest(GetPromptRequestParams(name = "Kotlin Developer", arguments = mapOf("Project Name" to "P")))
      )
    assertThat((prompt.messages.single().content as TextContent).text).contains("<name>P</name>")

    client.close()
  }
}
