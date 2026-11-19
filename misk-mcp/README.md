# misk-mcp

This module provides Misk with Model Context Protocol (MCP) server capabilities, enabling AI applications to seamlessly integrate with your services through standardized tools, resources, and prompts.

## What is MCP?

The [Model Context Protocol](https://modelcontextprotocol.io/specification/2025-06-18) is an open protocol that enables seamless integration between LLM applications and external data sources and tools. MCP provides a standardized way for applications to:

- **Share contextual information** with language models through resources and resource templates
- **Expose tools and capabilities** to AI systems for function execution  
- **Create reusable prompt templates** and workflows for users
- **Build composable integrations** across the AI ecosystem

The protocol uses JSON-RPC 2.0 messages over various transports to establish communication between hosts (LLM applications), clients (connectors), and servers (your services).

## Setup

Install the MCP server module in your Misk application:

```kotlin
@ExperimentalMiskApi
class MyAppModule : KAbstractModule() {
  override fun configure() {
    // Install the MCP server module with configuration
    install(McpServerModule.create("my_server", config.mcp))
    
    // Register your MCP components
    install(McpToolModule.create<CalculatorTool>())
    install(McpResourceModule.create<DatabaseSchemaResource>())
    install(McpResourceTemplateModule.create<UserProfileResource>())
    install(McpPromptModule.create<CodeReviewPrompt>())
  }
}
```

## Web Action Integration

To expose MCP functionality through HTTP, create a web action annotated with `@McpPost` and install it with a
WebActionModule. misk-mcp serves the Streamable HTTP transport: clients POST JSON-RPC messages and the server responds
with Server-Sent Events (SSE).

```kotlin
@ExperimentalMiskApi
class MyMcpPostAction @Inject constructor(
  private val mcpStreamManager: McpStreamManager
) : WebAction {

  @McpPost
  suspend fun handleMcpRequest(
    @RequestBody message: JSONRPCMessage,
    sendChannel: SendChannel<ServerSentEvent>
  ) {
    mcpStreamManager.withSseChannel(sendChannel) {
      handleMessage(message)
    }
  }
}
```

**Important**: You must install a WebActionModule to register your MCP web actions:

```kotlin
@ExperimentalMiskApi
class MyAppModule : KAbstractModule() {
  override fun configure() {
    // Install MCP server module
    install(McpServerModule.create("my_server", config.mcp))
    
    // Install web action module to register MCP endpoints
    install(WebActionModule.create<MyMcpWebAction>())
    
    // Register your MCP components
    install(McpToolModule.create<CalculatorTool>())
    install(McpResourceModule.create<DatabaseSchemaResource>())
  }
}
```

Without the WebActionModule installation, your `@McpPost` annotated methods will not be registered as HTTP endpoints.

## Configuration

Configure your MCP server in your application's YAML configuration:

```yaml
mcp:
  my_server:
    version: "1.0.0"
    prompts:
      list_changed: false
    resources:
      subscribe: false       # Enable resource update notifications (default: false)
      list_changed: false
    tools:
      list_changed: false
```

**Note**: Currently, only a single server configuration is supported. The configuration must contain exactly one server entry.

### Configuration Options

- **version**: Server version reported to clients (semantic versioning recommended)
- **resources.subscribe**: Enable resource update notifications (default: false)
- **list_changed**: Whether dynamic registration is supported (default: false, not currently implemented)

## WebAction Scoping Best Practices

MCP WebActions can be scoped as either **unscoped** (no annotation) or **`@Singleton`**. The choice affects how `McpStreamManager` and `MiskMcpServer` instances are created.

### Recommended: Unscoped WebActions (Default)

**Unscoped WebActions are the preferred pattern** because they create fresh `McpStreamManager` and `MiskMcpServer` instances per request, allowing for request-specific behavior.

```kotlin
@ExperimentalMiskApi
class MyMcpAction @Inject constructor(
  private val mcpStreamManager: McpStreamManager  // ✅ Fresh instance per request
) : WebAction {
  
  @McpPost
  suspend fun handleMcpRequest(
    @RequestBody message: JSONRPCMessage,
    sendChannel: SendChannel<ServerSentEvent>
  ) {
    mcpStreamManager.withSseChannel(sendChannel) {
      handleMessage(message)
    }
  }
}
```

**Benefits of unscoped WebActions:**
- Fresh server instance per request
- Tool/resource/prompt metadata can be request-specific
- Supports dynamic tool descriptions based on user context
- Simpler mental model - no need for providers

**Use unscoped WebActions when:**
- Tool descriptions vary by user permissions or roles
- Resource availability changes based on request context
- You have multi-tenant scenarios with different tool sets
- You want the flexibility for future dynamic behavior (recommended default)

### Alternative: Singleton WebActions with Provider

If you need a singleton WebAction (e.g., for expensive initialization), **you must inject `Provider<McpStreamManager>`** instead of `McpStreamManager` directly:

```kotlin
@ExperimentalMiskApi
@Singleton
class MyMcpAction @Inject constructor(
  private val mcpStreamManagerProvider: Provider<McpStreamManager>  // ✅ Use Provider
) : WebAction {
  
  @McpPost
  suspend fun handleMcpRequest(
    @RequestBody message: JSONRPCMessage,
    sendChannel: SendChannel<ServerSentEvent>
  ) {
    // Get a fresh instance per request
    mcpStreamManagerProvider.get().withSseChannel(sendChannel) {
      handleMessage(message)
    }
  }
}
```

**Important:** If you inject `McpStreamManager` directly into a `@Singleton` WebAction (without using `Provider`), you effectively create a singleton `MiskMcpServer` that is reused across all requests. While this works, it prevents request-specific tool metadata and should only be done if all your tool/resource/prompt descriptions are completely static.

```kotlin
@ExperimentalMiskApi
@Singleton
class MyMcpAction @Inject constructor(
  private val mcpStreamManager: McpStreamManager  // ⚠️ Effectively singleton server
) : WebAction {
  // This creates ONE MiskMcpServer instance for the lifetime of the application
  // Only use this pattern if ALL tool descriptions are static
}
```

### Comparison

| Pattern                                    | Server Lifecycle | Use Case |
|--------------------------------------------|------------------|----------|
| **Unscoped WebAction + Direct Injection** (Recommended)      | Fresh per request | Dynamic tool metadata, flexible, default choice |
| **Singleton WebAction + Provider**         | Fresh per request | Expensive WebAction initialization, still supports dynamic metadata |
| **Singleton WebAction + Direct Injection** | Singleton (one instance) | All tool/resource/prompt descriptions are completely static |

**Default recommendation:** Use unscoped WebActions for the best balance of flexibility and simplicity.

## Implementing MCP Components

### Tools

Tools are functions that AI models can execute. There are two types of tools:

#### Basic McpTool

Use `McpTool` when returning simple text or media content:

```kotlin
@Serializable
data class CalculatorInput(
  @Description("The mathematical operator (\"add\", \"subtract\", \"multiply\", or \"divide\") to execute")
  val operator: String,
  @Description("The first operand in the operation (a number)")
  val a: Double,
  @Description("The second operand in the operation (a number)")
  val b: Double
)

@ExperimentalMiskApi
@Singleton
class CalculatorTool @Inject constructor() : McpTool<CalculatorInput>() {
  override val name = "calculator"
  override val description = "Performs basic arithmetic operations"
  
  // Optional: Override tool hints to provide additional metadata to clients
  override val title = "Basic Calculator"  // User-friendly display name
  override val readOnlyHint = true  // This tool doesn't modify any state
  // Note: destructiveHint and idempotentHint are only relevant when readOnlyHint = false
  
  override suspend fun handle(input: CalculatorInput): ToolResult {
    val result = when (input.operator) {
      "add" -> input.a + input.b
      "subtract" -> input.a - input.b
      "multiply" -> input.a * input.b
      "divide" -> {
        if (input.b == 0.0) {
          return ToolResult(
            TextContent("Error: Division by zero"),
            isError = true
          )
        }
        input.a / input.b
      }
      else -> return ToolResult(
        TextContent("Unknown operator: ${input.operator}"),
        isError = true
      )
    }
    
    return ToolResult(
      TextContent("Result: $result")
    )
  }
}
```

#### StructuredMcpTool

Use `StructuredMcpTool` when returning structured data that AI models need to parse:

```kotlin
@Serializable
data class WeatherInput(
  @Description("The city to get weather for")
  val city: String,
  @Description("Temperature units: 'celsius' or 'fahrenheit'")
  val units: String = "celsius"
)

@Serializable
data class WeatherOutput(
  val city: String,
  val temperature: Double,
  val humidity: Int,
  val conditions: String,
  val windSpeed: Double,
  val units: String
)

@ExperimentalMiskApi
@Singleton
class WeatherTool @Inject constructor(
  private val weatherService: WeatherService
) : StructuredMcpTool<WeatherInput, WeatherOutput>() {
  override val name = "get_weather"
  override val description = "Get current weather conditions for a city"
  
  // Optional: Override tool hints
  override val title = "Weather Service"
  override val readOnlyHint = true  // Only reads weather data
  override val openWorldHint = true  // Interacts with external weather API
  
  override suspend fun handle(input: WeatherInput): ToolResult {
    return try {
      val weatherData = weatherService.getCurrentWeather(input.city, input.units)
      
      // Return structured data - will be serialized to JSON automatically
      ToolResult(
        WeatherOutput(
          city = input.city,
          temperature = weatherData.temp,
          humidity = weatherData.humidity,
          conditions = weatherData.description,
          windSpeed = weatherData.windSpeed,
          units = input.units
        )
      )
    } catch (e: CityNotFoundException) {
      // Can still return prompt content for errors
      ToolResult(
        TextContent("City '${input.city}' not found"),
        isError = true
      )
    }
  }
}
```

#### Accessing Request Meta (RequestMeta)

When a tool needs access to the inbound `CallToolRequest._meta` field — for example to read a `progressToken` so it can stream progress notifications back, or to inspect related-task metadata — extend one of the meta-aware bridge classes instead of `McpTool` / `StructuredMcpTool`:

- `MetaAwareMcpTool<I>` — for tools that return `ToolResult` (text/media/error content).
- `MetaAwareStructuredMcpTool<I, O>` — for tools that return structured `O` typed output.

Authors override the meta-aware `handle(input, meta)` overload; the bridge classes seal the input-only `handle(input)` overload to delegate with `meta = null`. The framework's dispatcher routes to the meta-aware overload automatically — no annotation or registration change is needed beyond picking the right base class.

```kotlin
@Serializable
data class LongRunningInput(
  @Description("How many steps the operation should run for")
  val steps: Int
)

@ExperimentalMiskApi
@Singleton
class LongRunningTool @Inject constructor(
  private val notifier: ProgressNotifier,
) : MetaAwareMcpTool<LongRunningInput>() {
  override val name = "long_running"
  override val description = "Performs a long-running operation with progress reporting"

  override suspend fun handle(input: LongRunningInput, meta: RequestMeta?): ToolResult {
    val token = meta?.progressToken
    repeat(input.steps) { step ->
      // ... do work ...
      if (token != null) {
        notifier.notify(token, progress = step + 1, total = input.steps)
      }
    }
    return ToolResult(TextContent("Completed ${input.steps} steps"))
  }
}
```

Use `MetaAwareStructuredMcpTool<I, O>` the same way; build the typed result with the inherited `ToolResult(result: O, ...)` factories. The interface-level return type is `McpTool.ToolResult`, not `StructuredToolResult<O>` — the typed `O` is preserved at value construction, not at signature.

Tools that don't need meta should keep extending `McpTool` / `StructuredMcpTool` — meta-awareness is strictly opt-in.

Register tools:

```kotlin
install(McpToolModule.create<CalculatorTool>())
install(McpToolModule.create<WeatherTool>())
```

#### Tool Hints

Tools can provide optional hints to help clients understand their behavior and requirements:

```kotlin
@ExperimentalMiskApi
@Singleton
class DatabaseTool @Inject constructor(
  private val database: Database
) : McpTool<DatabaseInput>() {
  override val name = "database_manager"
  override val description = "Manages database records"
  
  // Tool hints provide metadata about the tool's behavior
  override val title = "Database Manager"  // User-friendly display name
  
  override val readOnlyHint = false  // This tool modifies state
  
  // The following hints are only relevant when readOnlyHint = false:
  override val destructiveHint = true  // May delete or overwrite data
  override val idempotentHint = false  // Multiple calls may have different effects
  
  override val openWorldHint = true  // Interacts with external database
  
  override suspend fun handle(input: DatabaseInput): ToolResult {
    // Implementation
  }
}
```

**Available Tool Hints:**

- **`title`**: User-friendly display name for the tool (defaults to `name`)
- **`readOnlyHint`**: Whether the tool only reads data without modifying state (default: `false`)
- **`destructiveHint`**: Whether the tool may delete or overwrite data (default: `true`, only relevant when `readOnlyHint = false`)
- **`idempotentHint`**: Whether multiple calls with same input produce same result (default: `false`, only relevant when `readOnlyHint = false`)
- **`openWorldHint`**: Whether the tool interacts with external systems (default: `true`)

These hints help AI clients:
- Show appropriate warnings for destructive operations
- Optimize caching for read-only tools
- Implement retry logic for idempotent tools
- Understand external dependencies

#### Working with ToolResult

Both tool types use the `ToolResult` sealed interface for type-safe result handling:

```kotlin
// Return a simple text result
return ToolResult(TextContent("Success!"))

// Return multiple content items
return ToolResult(
  TextContent("Processing complete"),
  ImageContent(base64Data = imageData, mimeType = "image/png")
)

// Return an error result
return ToolResult(
  TextContent("Failed to process request"),
  isError = true
)

// Include metadata
return ToolResult(
  TextContent("Result with metadata"),
  _meta = JsonObject(mapOf("processTime" to JsonPrimitive(123)))
)

// For StructuredMcpTool - return structured data
return ToolResult(myOutputObject)
```

### Resources

Resources provide contextual data to AI models. Implement the `McpResource` interface:

```kotlin
@Singleton
class DatabaseSchemaResource @Inject constructor(
  private val schemaService: SchemaService
) : McpResource {
  override val uri = "schema://database/users"
  override val name = "User Database Schema"
  override val description = "Complete schema definition for the users database table"
  override val mimeType = "application/json"
  
  override suspend fun handler(request: ReadResourceRequest): ReadResourceResult {
    val schema = schemaService.getUserTableSchema()
    val schemaJson = Json.encodeToString(schema)
    
    return ReadResourceResult(
      contents = listOf(
        ResourceContent(
          uri = uri,
          mimeType = mimeType,
          text = schemaJson
        )
      )
    )
  }
}
```

Register the resource:

```kotlin
install(McpResourceModule.create<DatabaseSchemaResource>())
```

### Resource Templates

Resource templates provide parameterized resources using [RFC 6570](https://datatracker.ietf.org/doc/html/rfc6570) URI templates. They allow clients to read dynamic resources by extracting variables from the URI. Implement the `McpResourceTemplate` interface:

```kotlin
@Singleton
class UserProfileResource @Inject constructor(
  private val userService: UserService
) : McpResourceTemplate {
  override val uriTemplate = "users://{userId}/profile"
  override val name = "User Profile"
  override val description = "Profile information for a specific user"
  override val mimeType = "application/json"

  override suspend fun handler(
    request: ReadResourceRequest,
    variables: Map<String, String>,
  ): ReadResourceResult {
    val userId = variables["userId"] ?: error("Missing userId")
    val user = userService.getUser(userId)

    return ReadResourceResult(
      contents = listOf(
        TextResourceContents(
          text = Json.encodeToString(user),
          uri = request.uri,
          mimeType = mimeType,
        )
      )
    )
  }
}
```

Register the resource template:

```kotlin
install(McpResourceTemplateModule.create<UserProfileResource>())
```

### Prompts

Prompts are reusable templates for AI interactions. Implement the `McpPrompt` interface:

```kotlin
@Singleton
class CodeReviewPrompt @Inject constructor() : McpPrompt {
  override val name = "code_review"
  override val description = "Generate a code review prompt for the given programming language and code snippet"
  
  override val arguments = listOf(
    PromptArgument(
      name = "language",
      description = "The programming language of the code",
      required = true
    ),
    PromptArgument(
      name = "code", 
      description = "The code snippet to review",
      required = true
    ),
    PromptArgument(
      name = "focus",
      description = "Specific aspects to focus on (security, performance, style)",
      required = false
    )
  )
  
  override suspend fun handler(request: GetPromptRequest): GetPromptResult {
    val args = request.params.arguments ?: emptyMap()
    val language = args["language"] ?: "unknown"
    val code = args["code"] ?: ""
    val focus = args["focus"] ?: "general best practices"
    
    val promptText = """
      Please review the following $language code and provide feedback focusing on $focus:
      
      ```$language
      $code
      ```
      
      Please provide:
      1. Overall assessment
      2. Specific issues or improvements  
      3. Best practice recommendations
    """.trimIndent()
    
    return GetPromptResult(
      description = "Code review prompt for $language code",
      messages = listOf(
        PromptMessage(
          role = MessageRole.USER,
          content = TextContent(text = promptText)
        )
      )
    )
  }
}
```

Register the prompt:

```kotlin
install(McpPromptModule.create<CodeReviewPrompt>())
```

## Resource URI Conventions

Follow consistent URI patterns for resources:

see: https://modelcontextprotocol.io/specification/2025-06-18/server/resources#common-uri-schemes

- `schema://database/table_name` - Database schemas
- `docs://api/endpoint_name` - API documentation  
- `config://service/setting_name` - Configuration values
- `data://reference/dataset_name` - Reference datasets
- `file://path/to/file` - File system resources

## Security Considerations

MCP enables powerful capabilities through data access and code execution. Follow these security principles:

1. **User Consent**: Always require explicit user consent for data access and tool execution
2. **Input Validation**: Validate all tool inputs and resource requests
3. **Access Controls**: Use the right `@Authenticated` or access annotations on your MCP web actions to enforce valid access for sensitive tools and resources
4. **Resource Isolation**: Limit resource access to appropriate scopes
5. **Error Handling**: Avoid exposing sensitive information in error messages

## Testing

Test your MCP components with type-safe inputs:

```kotlin
@ExperimentalMiskApi
class MyMcpTest {
  @Test
  fun testCalculatorTool() {
    val tool = CalculatorTool()
    
    // Test successful calculation
    val result = runBlocking { 
      tool.handle(
        CalculatorInput(
          operator = "add",
          a = 5.0,
          b = 3.0
        )
      )
    }
    
    // Use kotlin.test assertions as per misk standards
    assertTrue(result is McpTool.PromptToolResult)
    val promptResult = result as McpTool.PromptToolResult
    assertEquals(1, promptResult.result.size)
    assertEquals("Result: 8.0", (promptResult.result[0] as TextContent).text)
    assertFalse(promptResult.isError)
  }
  
  @Test
  fun testWeatherTool() {
    val weatherService = mockk<WeatherService>()
    val tool = WeatherTool(weatherService)
    
    // Mock the weather service
    coEvery { 
      weatherService.getCurrentWeather("San Francisco", "celsius") 
    } returns WeatherData(
      temp = 18.5,
      humidity = 65,
      description = "Partly cloudy",
      windSpeed = 12.3
    )
    
    // Test structured output
    val result = runBlocking {
      tool.handle(
        WeatherInput(
          city = "San Francisco",
          units = "celsius"
        )
      )
    }
    
    assertTrue(result is StructuredMcpTool.StructuredToolResult<*>)
    val structuredResult = result as StructuredMcpTool.StructuredToolResult<WeatherOutput>
    assertEquals("San Francisco", structuredResult.result.city)
    assertEquals(18.5, structuredResult.result.temperature)
    assertEquals(65, structuredResult.result.humidity)
    assertFalse(structuredResult.isError)
  }
}
```

## Learn More

- [MCP Specification](https://modelcontextprotocol.io/specification/2025-06-18) - Official protocol specification
- [MCP Examples](https://modelcontextprotocol.io/examples) - Example implementations
- [MCP SDKs](https://modelcontextprotocol.io) - Available SDKs for different languages
- [MCP Testing Tool](https://modelcontextprotocol.io/legacy/tools/inspector) - Local dashboard to test MCP server against your local running Misk service (simply run `$ npx @modelcontextprotocol/inspector`)
