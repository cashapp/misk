# misk-mcp

This module adds [Model Context Protocol](https://modelcontextprotocol.io/specification/2026-07-28) (MCP) servers to
Misk services, so that AI applications can call your tools, read your resources and use your prompts.

misk-mcp implements MCP itself; it doesn't depend on an MCP SDK. Its protocol types live in `misk.mcp.protocol`.

## What is MCP?

MCP is an open protocol between LLM applications (hosts and their clients) and the services that give them context
(servers). A server offers:

- **Tools** that the model can call
- **Resources** and **resource templates** that give the model data
- **Prompts**: reusable templates for interactions with the model

Messages are JSON-RPC 2.0. misk-mcp serves the Streamable HTTP transport: clients POST each message and the server
answers with Server-Sent Events (SSE).

## Protocol versions

misk-mcp serves two eras of the protocol on the same endpoint, and picks one for each request:

| Era | Versions | How clients talk |
|-----|----------|------------------|
| Modern | `2026-07-28` | Stateless. Every request carries `protocolVersion`, `clientInfo` and `clientCapabilities` in `_meta`. Clients discover the server with `server/discover`. |
| Legacy | `2025-11-25`, `2025-06-18`, `2025-03-26`, `2024-11-05` | Clients open with `initialize`, then send the negotiated version in the `MCP-Protocol-Version` header. |

Your tools, resources and prompts don't need to know which era a client speaks. A few features exist only in the modern
era; this README calls them out.

There are no server-side sessions and no WebSocket transport. Handlers can't send requests to the client, so the server
doesn't support sampling, roots or logging.

## Setup

Install the MCP server module and register your MCP components:

```kotlin
@ExperimentalMiskApi
class MyAppModule(private val config: MyAppConfig) : KAbstractModule() {
  override fun configure() {
    install(McpServerModule.create("my_server", config.mcp))

    install(McpToolModule.create<CalculatorTool>())
    install(McpResourceModule.create<DatabaseSchemaResource>())
    install(McpResourceTemplateModule.create<UserProfileResource>())
    install(McpPromptModule.create<CodeReviewPrompt>())
    install(McpCompletionModule.create<LanguageCompletions>())

    install(WebActionModule.create<MyMcpPostAction>())
  }
}
```

## Web action

Serve the MCP endpoint with a web action whose method is annotated with `@McpPost`:

```kotlin
@ExperimentalMiskApi
class MyMcpPostAction @Inject constructor(
  private val mcpStreamManager: McpStreamManager,
) : WebAction {
  @McpPost
  suspend fun handleMcpRequest(
    @RequestBody message: JSONRPCMessage,
    sendChannel: SendChannel<ServerSentEvent>,
  ) {
    mcpStreamManager.withSseChannel(sendChannel) {
      handleMessage(message)
    }
  }
}
```

`@McpPost` serves `POST /mcp`. Each request runs its handler on the web action's coroutine, and the response ends when
the handler returns. Client notifications and responses get `202 Accepted`. `GET` and `DELETE` get
`405 Method Not Allowed`, because the server has neither standalone streams nor sessions.

The server checks each request against the Streamable HTTP rules before any handler runs. It rejects a request with a
JSON-RPC error and an HTTP error status:

| Status | Code | When |
|--------|------|------|
| 403 | `-32600` | The `Origin` header isn't in `allowed_origins` |
| 400 | `-32020` | `MCP-Protocol-Version`, `Mcp-Method` or `Mcp-Name` doesn't match a modern request's body (values may be `=?base64?…?=` encoded), or a legacy request sends a modern version without `_meta` |
| 400 | `-32022` | The protocol version isn't supported. `data` lists the supported versions. A legacy request with no version header means `2025-03-26`. |
| 400 | `-32602` | A modern request lacks its `_meta` |
| 404 | `-32601` | A modern client calls a method that the server doesn't serve |

Legacy clients get unknown methods as a `-32601` error with status 200, as before.

While a handler runs, the server sends an SSE comment every `keep_alive_interval_ms`. It keeps proxies from closing an
idle stream. When the client has gone away, the comment fails to write and the server cancels the handler.

### Scoping the web action

Leave the web action unscoped, as above. Misk then creates a `McpStreamManager` and a `MiskMcpServer` for each request,
so tool, resource and prompt metadata can depend on the caller.

If the web action must be a `@Singleton`, inject `Provider<McpStreamManager>` and call `get()` for each request.
Injecting `McpStreamManager` directly into a singleton also works, but then one server serves every request, so all
metadata must be static.

## Configuration

```yaml
mcp:
  my_server:
    version: "1.0.0"
    title: "My Service"
    website_url: "https://my-service.example.com"
    icons:
      - src: "https://my-service.example.com/icon.png"
        mime_type: "image/png"
        sizes: ["48x48"]
    allowed_origins:
      - "https://app.example.com"
    keep_alive_interval_ms: 15000
    request_state_key: "mcp-request-state"
```

| Option | Default | Description |
|--------|---------|-------------|
| `version` | required | The server's version, reported to clients. It isn't the protocol version. |
| `title` | none | A display name for the server. |
| `website_url` | none | The server's website, for clients to display. |
| `icons` | none | Icons for the server, each with a `src` (`https:` or `data:` URI) and optional `mime_type`, `sizes` and `theme` (`light` or `dark`). |
| `allowed_origins` | any | Origins that may call the server. A request with any other `Origin` header gets a 403. Requests without an `Origin` header, as from non-browser clients, are always allowed. |
| `keep_alive_interval_ms` | `15000` | How often to send an SSE comment while a handler runs. |
| `request_state_key` | none | The misk-crypto MAC key that signs the state of [multi round-trip requests](#asking-the-user-for-input). |
| `prompts`, `resources`, `tools`, `enforce_strict_capabilities` | | Deprecated and ignored. Components are registered statically, so the server never sends list-changed or resource-update notifications. |

Modern results carry `ttlMs: 0` and `cacheScope: "private"`, so clients don't cache list or read results across users.

## Tools

Tools are functions the model can call. Extend `McpTool` to return text or media content, or `StructuredMcpTool` to
return structured data. The input type's properties, with their `@Description`s, become the tool's input schema.

```kotlin
@Serializable
data class CalculatorInput(
  @Description("The operator: add, subtract, multiply or divide")
  val operator: String,
  @Description("The first operand")
  val a: Double,
  @Description("The second operand")
  val b: Double,
)

@ExperimentalMiskApi
@Singleton
class CalculatorTool @Inject constructor() : McpTool<CalculatorInput>() {
  override val name = "calculator"
  override val description = "Performs basic arithmetic"
  override val title = "Calculator"
  override val readOnlyHint = true

  override suspend fun handle(input: CalculatorInput): ToolResult {
    val result = when (input.operator) {
      "add" -> input.a + input.b
      "subtract" -> input.a - input.b
      "multiply" -> input.a * input.b
      "divide" -> {
        if (input.b == 0.0) return ToolResult(TextContent("Division by zero"), isError = true)
        input.a / input.b
      }
      else -> return ToolResult(TextContent("Unknown operator: ${input.operator}"), isError = true)
    }
    return ToolResult(TextContent("Result: $result"))
  }
}
```

A `StructuredMcpTool` declares an output type too, which becomes the tool's output schema:

```kotlin
@Serializable
data class WeatherInput(@Description("The city") val city: String)

@Serializable
data class WeatherOutput(val city: String, val temperature: Double, val conditions: String)

@ExperimentalMiskApi
@Singleton
class WeatherTool @Inject constructor(
  private val weatherService: WeatherService,
) : StructuredMcpTool<WeatherInput, WeatherOutput>() {
  override val name = "get_weather"
  override val description = "Gets the current weather in a city"
  override val readOnlyHint = true
  override val openWorldHint = true

  override suspend fun handle(input: WeatherInput): ToolResult {
    val weather = weatherService.current(input.city)
      ?: return ToolResult(TextContent("City '${input.city}' not found"), isError = true)
    return ToolResult(WeatherOutput(input.city, weather.temperature, weather.conditions))
  }
}
```

An exception thrown by a tool becomes an error result, with the text `Error executing tool <name>: <message>`. Throw
`McpException` to answer with a JSON-RPC error instead.

### Tool results

```kotlin
return ToolResult(TextContent("Success!"))

return ToolResult(
  TextContent("Processing complete"),
  ImageContent(data = base64Png, mimeType = "image/png"),
)

return ToolResult(TextContent("Failed to process request"), isError = true)

return ToolResult(TextContent("Done"), _meta = JsonObject(mapOf("processTime" to JsonPrimitive(123))))

// In a StructuredMcpTool
return ToolResult(myOutput)
```

### Tool hints

| Hint | Default | Meaning |
|------|---------|---------|
| `title` | `name` | A display name |
| `icons` | none | Icons for clients to display |
| `readOnlyHint` | `false` | The tool only reads data |
| `destructiveHint` | `true` | The tool may delete or overwrite data. Relevant only when `readOnlyHint` is false. |
| `idempotentHint` | `false` | Repeating a call with the same input has no further effect. Relevant only when `readOnlyHint` is false. |
| `openWorldHint` | `true` | The tool reaches systems outside the service |

### Request meta

To read a call's `_meta`, extend `MetaAwareMcpTool<I>` or `MetaAwareStructuredMcpTool<I, O>` and override
`handle(input, meta)`. `meta` is null when the request has no `_meta`.

```kotlin
@ExperimentalMiskApi
class AuditedTool @Inject constructor() : MetaAwareMcpTool<AuditInput>() {
  override val name = "audited"
  override val description = "Records who asked"

  override suspend fun handle(input: AuditInput, meta: RequestMeta?): ToolResult {
    val client = meta?.clientInfo?.name
    // ...
  }
}
```

## Progress

A tool, prompt or resource can report progress while it runs with `reportProgress`. The server sends each report as a
`notifications/progress` event on the request's stream, before the result. When the client didn't ask for progress
with a `_meta.progressToken`, `reportProgress` does nothing.

```kotlin
override suspend fun handle(input: ImportInput): ToolResult {
  input.files.forEachIndexed { index, file ->
    importer.import(file)
    reportProgress(progress = index + 1.0, total = input.files.size.toDouble(), message = "Imported $file")
  }
  return ToolResult(TextContent("Imported ${input.files.size} files"))
}
```

## Resources

Resources give the model data. Implement `McpResource`:

```kotlin
@Singleton
class DatabaseSchemaResource @Inject constructor(
  private val schemaService: SchemaService,
) : McpResource {
  override val uri = "schema://database/users"
  override val name = "users_schema"
  override val title = "Users Table Schema"
  override val description = "The schema of the users table"
  override val mimeType = "application/json"

  override suspend fun handler(request: ReadResourceRequest): ReadResourceResult =
    ReadResourceResult(
      contents = listOf(
        TextResourceContents(text = schemaService.usersSchemaJson(), uri = uri, mimeType = mimeType)
      )
    )
}
```

### Resource templates

Resource templates serve parameterized resources through [RFC 6570](https://datatracker.ietf.org/doc/html/rfc6570) URI
templates. misk-mcp matches `{var}` and `{+var}` expressions and passes the variables to the handler.

```kotlin
@Singleton
class UserProfileResource @Inject constructor(
  private val userService: UserService,
) : McpResourceTemplate {
  override val uriTemplate = "users://{userId}/profile"
  override val name = "user_profile"
  override val description = "A user's profile"
  override val mimeType = "application/json"

  override suspend fun handler(request: ReadResourceRequest, variables: Map<String, String>): ReadResourceResult {
    val user = userService.getUser(variables.getValue("userId"))
    return ReadResourceResult(
      contents = listOf(TextResourceContents(text = Json.encodeToString(user), uri = request.uri, mimeType = mimeType))
    )
  }
}
```

Reading an unknown resource is a `-32602` error, or `-32002` for legacy clients.

Use consistent URI schemes, such as `schema://`, `docs://`, `config://`, `data://` and `file://`. See
[Common URI schemes](https://modelcontextprotocol.io/specification/2026-07-28/server/resources#common-uri-schemes).

## Prompts

Prompts are templates for interactions with the model. Implement `McpPrompt`:

```kotlin
@Singleton
class CodeReviewPrompt @Inject constructor() : McpPrompt {
  override val name = "code_review"
  override val title = "Code Review"
  override val description = "Asks for a review of a code snippet"
  override val arguments = listOf(
    PromptArgument(name = "language", description = "The code's language", required = true),
    PromptArgument(name = "code", description = "The code to review", required = true),
  )

  override suspend fun handler(request: GetPromptRequest): GetPromptResult {
    val args = request.params.arguments.orEmpty()
    val text = "Review this ${args["language"]} code:\n\n${args["code"]}"
    return GetPromptResult(
      description = "Code review",
      messages = listOf(PromptMessage(role = Role.User, content = TextContent(text))),
    )
  }
}
```

The server checks that required arguments are present. A missing argument, like an unknown prompt, is a `-32602`
error.

## Completion

An `McpCompletionProvider` suggests values for the arguments of one prompt, or the variables of one resource template,
through `completion/complete`:

```kotlin
class LanguageCompletions @Inject constructor() : McpCompletionProvider {
  override val ref = PromptReference("code_review")

  override suspend fun complete(request: CompleteRequest): List<String> =
    when (request.argument.name) {
      "language" -> listOf("java", "kotlin", "swift").filter { it.startsWith(request.argument.value) }
      else -> emptyList()
    }
}

install(McpCompletionModule.create<LanguageCompletions>())
```

The server advertises the `completions` capability only when at least one provider is registered. It returns the first
100 values, with the total count and `hasMore`. A prompt or template without a provider completes to no values, and an
unknown reference is a `-32602` error. Each provider's `ref` must name a registered prompt or resource template, and
only one provider may serve each.

## Asking the user for input

A tool, prompt or resource can ask the user for input partway through a request, with a *multi round-trip request*
(MRTR). The handler ends the request with `inputRequired`. The client collects the input, usually by asking the user,
and sends the same request again with the answers, which the handler reads with `inputResponse`:

```kotlin
@Serializable
data class Nickname(@Description("What to call you") val nickname: String)

override suspend fun handle(): ToolResult {
  val answer = inputResponse<Nickname>("nickname")
    ?: inputRequired { elicit<Nickname>("nickname", "What should I call you?") }

  return when (answer.action) {
    ElicitResult.Action.Accept -> ToolResult(TextContent("Hello, ${answer.content!!.nickname}!"))
    ElicitResult.Action.Decline, ElicitResult.Action.Cancel -> ToolResult(TextContent("Hello!"))
  }
}
```

- `elicit<T>(key, message)` asks for a form whose fields are `T`'s properties. Fields can be strings, numbers, booleans
  and enums, and `@Description` describes them.
- `elicitUrl(key, message, url)` sends the user to a URL, for input that must not pass through the client, such as a
  credential.
- `elicit(key, params)` takes `ElicitRequestFormParams` or `ElicitRequestURLParams` directly.

`inputRequired` never returns. It ends the request by throwing, and a handler that catches the exception still ends
with the input request. The `catch (e: Exception)` block and the code after it still run, though, and their result is
discarded. Keep side effects such as error logs and failure metrics out of them, and catch narrower exceptions where you
can.

Each round runs the handler from the start, so work before `inputRequired` runs again.
To carry results to the next round, pass a state, and read it back with `requestState`:

```kotlin
@Serializable
data class TransferState(val quoteId: String)

override suspend fun handle(input: TransferInput): ToolResult {
  val state = requestState<TransferState>()
    ?: inputRequired(TransferState(quotes.create(input).id)) {
      elicit<Confirmation>("confirm", "Send ${input.amount} to ${input.recipient}?")
    }
  val confirmation = inputResponse<Confirmation>("confirm")!!
  // ...
}
```

The server signs the state with the misk-crypto MAC key named by `request_state_key`, so the client can't read or
forge it. Install misk-crypto's `CryptoModule` with that key. The state is bound to the caller and to the request's
method and parameters, and it expires after 10 minutes. A state that fails any of these checks is a `-32602` error.

The server checks the client's capabilities before it sends the input requests. A client that doesn't declare
`elicitation.form`, or `elicitation.url` for a URL request, gets a `-32021` error whose `data.requiredCapabilities`
lists what's missing.

Only `tools/call`, `prompts/get` and `resources/read` can require input, and only from modern clients. A legacy client
gets an error result from a tool, and a `-32021` error from a prompt or resource.

## Security considerations

1. **Access control**: put `@Authenticated` or another access annotation on your MCP web action.
2. **Origins**: set `allowed_origins` when browsers can reach the server, to block DNS rebinding attacks.
3. **Input validation**: validate tool inputs and resource URIs.
4. **Errors**: keep sensitive details out of error messages, including the messages of exceptions that tools throw.
5. **User consent**: hosts ask users before running tools. Use the tool hints to describe what a tool does.

## Testing

Test a tool's logic by calling `handle` directly:

```kotlin
@Test
fun adds() = runBlocking {
  val result = CalculatorTool().handle(CalculatorInput(operator = "add", a = 5.0, b = 3.0))

  val content = (result as McpTool.PromptToolResult).result
  assertEquals("Result: 8.0", (content.single() as TextContent).text)
}
```

### End-to-end tests

`McpTestClient`, in misk-mcp's test fixtures, calls your `@McpPost` endpoint over Streamable HTTP. It speaks the modern
protocol by default. Pass a legacy `protocolVersion` to test legacy clients; the client opens with `initialize` before
its first request.

```kotlin
dependencies {
  testImplementation(testFixtures("com.squareup.misk:misk-mcp"))
}
```

```kotlin
@MiskTest(startService = true)
class MyMcpEndpointTest {
  @MiskTestModule val module = MyTestModule()

  @Inject lateinit var jettyService: JettyService

  @Test
  fun callsCalculator() = runBlocking {
    val client = McpTestClient(OkHttpClient(), jettyService.httpServerUrl.resolve("/mcp")!!)

    val result = client.callTool("calculator", mapOf("operator" to "add", "a" to 5.0, "b" to 3.0))

    assertEquals("Result: 8.0", (result.content.single() as TextContent).text)
  }
}
```

To test a handler that asks for input, declare the elicitation capability and pass an `InputHandler`. The client
answers each input request and retries until the request completes:

```kotlin
val client = McpTestClient(
  OkHttpClient(),
  url,
  capabilities = ClientCapabilities(elicitation = ClientCapabilities.Elicitation()),
  inputHandler = { key, request ->
    ElicitResult(ElicitResult.Action.Accept, buildJsonObject { put("nickname", "Ada") })
  },
)
```

`serverInfo()` returns the server's name and version in either era. `McpTestClient.send` returns the whole response,
including its HTTP status and progress notifications, for tests of the protocol itself. The client holds no connection
or session, so there is nothing to close.

## Migrating from kotlin-sdk

misk-mcp used to build on the Kotlin MCP SDK. Its types moved to `misk.mcp.protocol` with the same names and
constructor parameters, so most code migrates by rewriting imports. `scripts/migrate-from-kotlin-sdk.sh` rewrites them
and lists what needs a person. See the CHANGELOG for the full list of changes.

## Learn more

- [MCP specification](https://modelcontextprotocol.io/specification/2026-07-28)
- [MCP Inspector](https://modelcontextprotocol.io/docs/tools/inspector): a local dashboard for testing your server
  (`npx @modelcontextprotocol/inspector`)
