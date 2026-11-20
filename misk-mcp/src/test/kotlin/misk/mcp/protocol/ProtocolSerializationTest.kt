package misk.mcp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import misk.mcp.McpJson
import org.assertj.core.api.Assertions.assertThat

class ProtocolSerializationTest {
  @Test
  fun `JSON-RPC messages round-trip`() {
    val examples =
      listOf(
        """{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"cursor":"c"}}""",
        """{"jsonrpc":"2.0","id":"abc","method":"server/discover"}""",
        """{"jsonrpc":"2.0","method":"notifications/progress","params":{"progressToken":"t","progress":1}}""",
        """{"jsonrpc":"2.0","id":1,"result":{"resultType":"complete"}}""",
        """{"jsonrpc":"2.0","id":2,"error":{"code":-32602,"message":"Unknown tool: x"}}""",
        """{"jsonrpc":"2.0","error":{"code":-32020,"message":"Header mismatch"}}""",
        """{"jsonrpc":"2.0","id":1,"error":{"code":-32022,"message":"Unsupported protocol version",""" +
          """"data":{"supported":["2026-07-28"],"requested":"1900-01-01"}}}""",
      )

    for (example in examples) {
      val message = McpJson.decodeFromString(JSONRPCMessage.serializer(), example)
      assertEquals(parse(example), McpJson.encodeToJsonElement(JSONRPCMessage.serializer(), message), example)
    }
  }

  @Test
  fun `decodes each JSON-RPC message type`() {
    assertThat(decodeMessage("""{"jsonrpc":"2.0","id":7,"method":"ping"}"""))
      .isEqualTo(JSONRPCRequest(id = 7L, method = "ping"))
    assertThat(decodeMessage("""{"jsonrpc":"2.0","method":"notifications/initialized"}"""))
      .isEqualTo(JSONRPCNotification(method = "notifications/initialized"))
    assertThat(decodeMessage("""{"jsonrpc":"2.0","id":"x","result":{}}"""))
      .isEqualTo(JSONRPCResponse(id = RequestId("x")))
    assertThat(decodeMessage("""{"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"Parse error"}}"""))
      .isEqualTo(JSONRPCError(id = null, error = RPCError(RPCError.ErrorCode.PARSE_ERROR, "Parse error")))
  }

  @Test
  fun `tools list result from the spec`() {
    val result =
      decodeAndRoundTrip(
        ListToolsResult.serializer(),
        """
        {
          "resultType": "complete",
          "tools": [{
            "name": "get_weather",
            "title": "Weather Information Provider",
            "description": "Get current weather information for a location",
            "inputSchema": {
              "type": "object",
              "properties": {"location": {"type": "string", "description": "City name or zip code"}},
              "required": ["location"]
            },
            "icons": [{"src": "https://example.com/weather-icon.png", "mimeType": "image/png", "sizes": ["48x48"]}]
          }],
          "nextCursor": "next-page-cursor",
          "ttlMs": 300000,
          "cacheScope": "public"
        }
        """,
        ignoredKeys = setOf("resultType", "ttlMs", "cacheScope"),
      )

    val tool = result.tools.single()
    assertEquals(listOf("location"), tool.inputSchema.required)
    assertEquals(
      Icon(src = "https://example.com/weather-icon.png", mimeType = "image/png", sizes = listOf("48x48")),
      tool.icons?.single(),
    )
  }

  @Test
  fun `content blocks keep their type discriminator`() {
    val result =
      decodeAndRoundTrip(
        CallToolResult.serializer(),
        """
        {
          "content": [
            {"type": "text", "text": "hi", "annotations": {"audience": ["user"], "priority": 0.5}},
            {"type": "image", "data": "aGk=", "mimeType": "image/png"},
            {"type": "audio", "data": "aGk=", "mimeType": "audio/wav"},
            {"type": "resource_link", "uri": "file:///a.rs", "name": "a.rs", "mimeType": "text/x-rust"},
            {"type": "resource", "resource": {"uri": "file:///b.bin", "blob": "AAE=", "mimeType": "application/octet-stream"}},
            {"type": "resource", "resource": {"uri": "file:///c.txt", "text": "c"}}
          ],
          "structuredContent": [1, 2, 3],
          "isError": false
        }
        """,
      )

    assertThat(result.content.map { it.type })
      .containsExactly("text", "image", "audio", "resource_link", "resource", "resource")
    assertIs<BlobResourceContents>((result.content[4] as EmbeddedResource).resource)
    assertIs<TextResourceContents>((result.content[5] as EmbeddedResource).resource)
    assertEquals(parse("[1,2,3]"), result.structuredContent)
    assertEquals(
      parse("""{"type":"text","text":"x"}"""),
      McpJson.encodeToJsonElement(ContentBlock.serializer(), TextContent(text = "x")),
    )
  }

  @Test
  fun `input required result from the spec`() {
    val result =
      decodeAndRoundTrip(
        InputRequiredResult.serializer(),
        """
        {
          "resultType": "input_required",
          "inputRequests": {
            "github_login": {
              "method": "elicitation/create",
              "params": {
                "mode": "form",
                "message": "Please provide your GitHub username",
                "requestedSchema": {"type": "object", "properties": {"name": {"type": "string"}}, "required": ["name"]}
              }
            },
            "auth": {
              "method": "elicitation/create",
              "params": {"mode": "url", "message": "Sign in", "url": "https://example.com/auth"}
            }
          },
          "requestState": "opaque"
        }
        """,
      )

    val form = (result.inputRequests!!.getValue("github_login") as ElicitRequest).params
    assertIs<ElicitRequestFormParams>(form)
    assertIs<StringSchema>(form.requestedSchema.properties.getValue("name"))
    assertIs<ElicitRequestURLParams>((result.inputRequests.getValue("auth") as ElicitRequest).params)
  }

  @Test
  fun `elicitation primitive schemas`() {
    val schema =
      decodeAndRoundTrip(
        RequestedSchema.serializer(),
        """
        {
          "type": "object",
          "properties": {
            "email": {"type": "string", "format": "email", "maxLength": 50},
            "age": {"type": "integer", "minimum": 0},
            "score": {"type": "number", "maximum": 1.5},
            "ok": {"type": "boolean", "default": true},
            "color": {"type": "string", "enum": ["red", "blue"]},
            "size": {"type": "string", "oneOf": [{"const": "s", "title": "Small"}]},
            "tags": {"type": "array", "items": {"type": "string", "enum": ["a", "b"]}},
            "labels": {"type": "array", "items": {"anyOf": [{"const": "x", "title": "X"}]}}
          },
          "required": ["email"]
        }
        """,
      )

    assertThat(schema.properties.values.map { it::class })
      .containsExactly(
        StringSchema::class,
        IntegerSchema::class,
        NumberSchema::class,
        BooleanSchema::class,
        UntitledSingleSelectEnumSchema::class,
        TitledSingleSelectEnumSchema::class,
        UntitledMultiSelectEnumSchema::class,
        TitledMultiSelectEnumSchema::class,
      )
  }

  @Test
  fun `elicit result`() {
    val result = decodeAndRoundTrip(ElicitResult.serializer(), """{"action":"accept","content":{"name":"octocat"}}""")
    assertEquals(ElicitResult.Action.Accept, result.action)
  }

  @Test
  fun `resources and prompts`() {
    decodeAndRoundTrip(
      ListResourcesResult.serializer(),
      """{"resources":[{"uri":"file:///main.rs","name":"main.rs","title":"Main","mimeType":"text/x-rust","size":12}]}""",
    )
    decodeAndRoundTrip(
      ListResourceTemplatesResult.serializer(),
      """{"resourceTemplates":[{"uriTemplate":"file:///{path}","name":"Project Files","mimeType":"application/octet-stream"}]}""",
    )
    decodeAndRoundTrip(
      ReadResourceResult.serializer(),
      """{"contents":[{"uri":"file:///main.rs","mimeType":"text/x-rust","text":"fn main() {}"}]}""",
    )
    val prompts =
      decodeAndRoundTrip(
        ListPromptsResult.serializer(),
        """{"prompts":[{"name":"code_review","title":"Request Code Review","arguments":[{"name":"code","required":true}]}]}""",
      )
    assertEquals(true, prompts.prompts.single().arguments?.single()?.required)
    val prompt =
      decodeAndRoundTrip(
        GetPromptResult.serializer(),
        """{"description":"Code review prompt","messages":[{"role":"user","content":{"type":"text","text":"Review"}}]}""",
      )
    assertEquals(Role.User, prompt.messages.single().role)
  }

  @Test
  fun `requests carry params and typed meta`() {
    val params =
      McpJson.decodeFromString(
        CallToolRequestParams.serializer(),
        """
        {
          "name": "get_weather",
          "arguments": {"location": "New York"},
          "_meta": {
            "io.modelcontextprotocol/protocolVersion": "2026-07-28",
            "io.modelcontextprotocol/clientInfo": {"name": "ExampleClient", "version": "1.0.0"},
            "io.modelcontextprotocol/clientCapabilities": {"elicitation": {}},
            "progressToken": 42,
            "traceparent": "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01"
          },
          "inputResponses": {"github_login": {"action": "accept", "content": {"name": "octocat"}}},
          "requestState": "opaque"
        }
        """,
      )
    val request = CallToolRequest(params)

    val meta = request.meta!!
    assertEquals("2026-07-28", meta.protocolVersion)
    assertEquals(Implementation(name = "ExampleClient", version = "1.0.0"), meta.clientInfo)
    assertEquals(true, meta.clientCapabilities?.elicitation?.supportsForm)
    assertEquals(false, meta.clientCapabilities?.elicitation?.supportsUrl)
    assertEquals(ProgressToken(42), meta.progressToken)
    assertEquals(JsonPrimitive("00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01"), meta["traceparent"])
    assertEquals("opaque", request.requestState)
    assertEquals(setOf("github_login"), request.inputResponses?.keys)
  }

  @Test
  fun `discover result`() {
    val result =
      decodeAndRoundTrip(
        DiscoverResult.serializer(),
        """
        {
          "resultType": "complete",
          "supportedVersions": ["2026-07-28"],
          "capabilities": {"tools": {}, "resources": {}, "extensions": {"io.modelcontextprotocol/tasks": {}}},
          "_meta": {"io.modelcontextprotocol/serverInfo": {"name": "ExampleServer", "version": "1.0.0"}},
          "instructions": "This server provides weather and resource utilities.",
          "ttlMs": 3600000,
          "cacheScope": "public"
        }
        """,
        ignoredKeys = setOf("resultType", "ttlMs", "cacheScope"),
      )
    assertEquals(setOf("io.modelcontextprotocol/tasks"), result.capabilities.extensions?.keys)
  }

  @Test
  fun `legacy initialize from the 2025-11-25 spec`() {
    val request =
      McpJson.decodeFromString(
        InitializeRequestParams.serializer(),
        """
        {
          "protocolVersion": "2025-11-25",
          "capabilities": {"roots": {"listChanged": true}, "sampling": {}, "elicitation": {"form": {}, "url": {}}},
          "clientInfo": {
            "name": "ExampleClient",
            "title": "Example Client Display Name",
            "version": "1.0.0",
            "description": "An example MCP client application",
            "icons": [{"src": "https://example.com/icon.png", "mimeType": "image/png", "sizes": ["48x48"]}],
            "websiteUrl": "https://example.com"
          }
        }
        """,
      )
    assertEquals("2025-11-25", request.protocolVersion)
    assertEquals(true, request.capabilities.elicitation?.supportsUrl)

    decodeAndRoundTrip(
      InitializeResult.serializer(),
      """
      {
        "protocolVersion": "2025-11-25",
        "capabilities": {"logging": {}, "prompts": {"listChanged": true}, "resources": {"subscribe": true, "listChanged": true}, "tools": {"listChanged": true}},
        "serverInfo": {"name": "ExampleServer", "version": "1.0.0", "title": "Example Server Display Name"},
        "instructions": "Optional instructions for the client"
      }
      """,
    )
  }

  @Test
  fun `completion request and result`() {
    val params =
      McpJson.decodeFromString(
        CompleteRequestParams.serializer(),
        """{"ref":{"type":"ref/prompt","name":"code_review"},"argument":{"name":"language","value":"py"},"context":{"arguments":{"a":"b"}}}""",
      )
    assertEquals(PromptReference(name = "code_review"), params.ref)
    decodeAndRoundTrip(
      CompleteResult.serializer(),
      """{"completion":{"values":["python","pytorch","pyside"],"total":10,"hasMore":true}}""",
    )
  }

  @Test
  fun `progress notification params`() {
    decodeAndRoundTrip(
      ProgressNotificationParams.serializer(),
      """{"progressToken":"abc123","progress":50.0,"total":100.0,"message":"Reticulating splines..."}""",
    )
  }

  private fun decodeMessage(json: String) = McpJson.decodeFromString(JSONRPCMessage.serializer(), json)

  private fun parse(json: String) = McpJson.parseToJsonElement(json)

  private fun <T> decodeAndRoundTrip(
    serializer: KSerializer<T>,
    json: String,
    ignoredKeys: Set<String> = emptySet(),
  ): T {
    val value = McpJson.decodeFromString(serializer, json)
    val expected = JsonObject(parse(json).jsonObject.filterKeys { it !in ignoredKeys })
    assertEquals(expected, McpJson.encodeToJsonElement(serializer, value))
    return value
  }
}
