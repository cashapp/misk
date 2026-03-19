@file:OptIn(ExperimentalMiskApi::class)

package misk.web.mcp

import com.google.inject.Module
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ListPromptsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import jakarta.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import misk.annotation.ExperimentalMiskApi
import misk.mcp.testing.asMcpStreamableHttpClient
import misk.security.authz.FakeCallerAuthenticator
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import misk.web.metadata.MetadataTestingModule
import okhttp3.OkHttpClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class ContainerAdminMcpWebActionTest {

  @MiskTestModule val module: Module = MetadataTestingModule()

  @Inject lateinit var jettyService: JettyService

  private lateinit var mcpClient: Client

  @BeforeEach
  fun setUp() = runBlocking {
    val authenticatedClient =
      OkHttpClient.Builder()
        .addInterceptor { chain ->
          chain.proceed(
            chain
              .request()
              .newBuilder()
              .header(FakeCallerAuthenticator.USER_HEADER, "test-user")
              .header(FakeCallerAuthenticator.CAPABILITIES_HEADER, "admin_access,admin_console")
              .build()
          )
        }
        .build()

    mcpClient = authenticatedClient.asMcpStreamableHttpClient(baseUrl = jettyService.httpServerUrl, path = "/admin/mcp")
  }

  @Test
  fun `list tools returns get_metadata`(): Unit = runBlocking {
    val response = mcpClient.listTools(ListToolsRequest())
    val toolNames = response.tools.map { it.name }
    assertTrue("get_metadata" in toolNames, "Expected get_metadata tool, got: $toolNames")

    val tool = response.tools.first { it.name == "get_metadata" }
    assertNotNull(tool.description)
    assertNotNull(tool.inputSchema)
  }

  @Test
  fun `list resources returns metadata resources`() = runBlocking {
    val response = mcpClient.listResources(ListResourcesRequest())
    val resourceUris = response.resources.map { it.uri }.toSet()

    assertTrue("admin://metadata/config" in resourceUris, "Expected config resource, got: $resourceUris")
    assertTrue("admin://metadata/jvm" in resourceUris, "Expected jvm resource, got: $resourceUris")
    assertTrue("admin://metadata/web-actions" in resourceUris, "Expected web-actions resource, got: $resourceUris")
    assertTrue("admin://metadata/guice" in resourceUris, "Expected guice resource, got: $resourceUris")
    assertTrue("admin://metadata/service-graph" in resourceUris, "Expected service-graph resource, got: $resourceUris")
    assertTrue(
      "admin://metadata/database-hibernate" in resourceUris,
      "Expected database-hibernate resource, got: $resourceUris",
    )
  }

  @Test
  fun `list prompts returns container_admin`() = runBlocking {
    val response = mcpClient.listPrompts(ListPromptsRequest())
    val promptNames = response.prompts.map { it.name }
    assertTrue("container_admin" in promptNames, "Expected container_admin prompt, got: $promptNames")
  }

  @Test
  fun `get_metadata tool returns jvm metadata`() = runBlocking {
    val response =
      mcpClient.callTool(
        CallToolRequest(
          CallToolRequestParams(name = "get_metadata", arguments = buildJsonObject { put("id", JsonPrimitive("jvm")) })
        )
      )
    assertNotNull(response)
    assertEquals(false, response.isError)

    val text = (response.content.first() as TextContent).text
    assertTrue(text.contains("jvm"), "Expected JVM metadata content, got: $text")
  }

  @Test
  fun `get_metadata tool with invalid id returns error`() = runBlocking {
    val response =
      mcpClient.callTool(
        CallToolRequest(
          CallToolRequestParams(
            name = "get_metadata",
            arguments = buildJsonObject { put("id", JsonPrimitive("nonexistent")) },
          )
        )
      )
    assertNotNull(response)
    assertEquals(true, response.isError)

    val text = (response.content.first() as TextContent).text
    assertTrue(text.contains("No metadata found"), "Expected error message, got: $text")
  }

  @Test
  fun `read config resource returns config data`() = runBlocking {
    val response =
      mcpClient.readResource(ReadResourceRequest(ReadResourceRequestParams(uri = "admin://metadata/config")))
    assertNotNull(response)
    assertEquals(1, response.contents.size)

    val content = response.contents.first() as TextResourceContents
    assertEquals("admin://metadata/config", content.uri)
    assertTrue(content.text.contains("Effective Config"), "Expected config content, got: ${content.text}")
  }

  @Test
  fun `read jvm resource returns jvm data`() = runBlocking {
    val response = mcpClient.readResource(ReadResourceRequest(ReadResourceRequestParams(uri = "admin://metadata/jvm")))
    assertNotNull(response)
    assertEquals(1, response.contents.size)

    val content = response.contents.first() as TextResourceContents
    assertEquals("admin://metadata/jvm", content.uri)
    assertTrue(content.text.isNotBlank(), "Expected non-empty JVM content")
  }
}
