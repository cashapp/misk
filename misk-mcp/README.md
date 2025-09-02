# misk-mcp

This module provides Misk with Model Context Protocol (MCP) server capabilities, enabling AI applications to seamlessly integrate with your services through standardized tools, resources, and prompts.

## What is MCP?

The [Model Context Protocol](https://modelcontextprotocol.io/specification/2025-06-18) is an open protocol that enables seamless integration between LLM applications and external data sources and tools. MCP provides a standardized way for applications to:

- **Share contextual information** with language models through resources
- **Expose tools and capabilities** to AI systems for function execution  
- **Create reusable prompt templates** and workflows for users
- **Build composable integrations** across the AI ecosystem

The protocol uses JSON-RPC 2.0 messages over various transports to establish communication between hosts (LLM applications), clients (connectors), and servers (your services).

## Setup

Install the MCP server module in your Misk application:

```kotlin
class MyAppModule : KAbstractModule() {
  override fun configure() {
    // Install the MCP server module with configuration
    install(McpServerModule(config.mcp))
    
    // Register your MCP components
    install(McpToolModule.create<CalculatorTool>())
    install(McpResourceModule.create<DatabaseSchemaResource>())
    install(McpPromptModule.create<CodeReviewPrompt>())
  }
}
```

## Web Action Integration

To expose MCP functionality through HTTP endpoints, you need to create web actions using the MCP annotations and install the appropriate WebActionModule.

### Required and Optional Endpoints

- **`@McpPost`** (Required): Handles incoming MCP requests from clients
- **`@McpGet`** (Optional): Enables out-of-band server-to-client notifications, typically used when a stateful session is present
- **`@McpDelete`** (Optional): Allows clients to explicitly delete an existing stateful session

```kotlin
@Singleton
class MyMcpWebAction @Inject constructor(
  private val mcpSessionManager: McpSessionManager
) : WebAction {

  @McpPost
  suspend fun handleMcpRequest(
    @RequestBody message: JSONRPCMessage,
    @RequestHeaders headers: Headers,
    sendChannel: SendChannel<ServerSentEvent>
  ) {
    val sessionId = headers[SESSION_ID_PARAM]
    mcpSessionManager.withResponseChannel(sendChannel) {
      handleMessage(message)
    }
  }

  @McpGet  
  suspend fun streamServerEvents(
    @RequestHeaders headers: Headers,
    sendChannel: SendChannel<ServerSentEvent>
  ) {
    val sessionId = headers[SESSION_ID_PARAM]
    mcpSessionManager.withResponseChannel(sendChannel) {
      // Stream server-initiated events to client
    }
  }

  @McpDelete
  suspend fun terminateSession(
    @RequestHeaders headers: Headers,
    sendChannel: SendChannel<ServerSentEvent>
  ) {
    val sessionId = requireRequestNotNull(headers[SESSION_ID_PARAM] ){
        "Missing session ID"
    }
    // Any session state should be cleaned up here
  }
}
```

**Important**: You must install a WebActionModule to register your MCP web actions:

```kotlin
class MyAppModule : KAbstractModule() {
  override fun configure() {
    // Install MCP server module
    install(McpServerModule(config.mcp))
    
    // Install web action module to register MCP endpoints
    install(WebActionModule.create<MyMcpWebAction>())
    
    // Register your MCP components
    install(McpToolModule.create<CalculatorTool>())
    install(McpResourceModule.create<DatabaseSchemaResource>())
  }
}
```

Without the WebActionModule installation, your `@McpPost`, `@McpGet`, and `@McpDelete` annotated methods will not be registered as HTTP endpoints.

## Stateful Sessions (Optional)

The MCP server supports stateful sessions using the `Mcp-Session-Id` header. This is optional and allows clients (like LLMs) to maintain context across multiple requests.

```kotlin
const val SESSION_ID_PARAM = "Mcp-Session-Id"
```

When a client includes the `Mcp-Session-Id` header in requests:
- The server maintains session state between requests
- Server-to-client notifications can be sent via the `@McpGet` endpoint
- Sessions can be explicitly terminated using the `@McpDelete` endpoint

If no session ID is provided, the server operates in a stateless mode where each request is independent.

## Configuration

Configure your MCP server in your application's YAML configuration:

```yaml
mcp:
  my_server:
    version: "1.0.0"
    prompts:
      list_changed: false
    resources:
      subscribe: true        # Enable resource update notifications
      list_changed: false
    tools:
      list_changed: false
```

**Note**: Currently, only a single server configuration is supported. The configuration must contain exactly one server entry.

### Configuration Options

- **version**: Server version reported to clients (semantic versioning recommended)
- **resources.subscribe**: Enable resource update notifications (default: false)
- **list_changed**: Whether dynamic registration is supported (default: false, not currently implemented)

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

@Singleton
class CalculatorTool @Inject constructor() : McpTool<CalculatorInput>(
  inputClass = CalculatorInput::class
) {
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

@Singleton
class WeatherTool @Inject constructor(
  private val weatherService: WeatherService
) : StructuredMcpTool<WeatherInput, WeatherOutput>(
  inputClass = WeatherInput::class,
  outputClass = WeatherOutput::class
) {
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

Register tools:

```kotlin
install(McpToolModule.create<CalculatorTool>())
install(McpToolModule.create<WeatherTool>())
```

#### Tool Hints

Tools can provide optional hints to help clients understand their behavior and requirements:

```kotlin
@Singleton
class DatabaseTool @Inject constructor(
  private val database: Database
) : McpTool<DatabaseInput>(DatabaseInput::class) {
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

## Resource Update Notifications

When you enable resource subscriptions (`resources.subscribe: true`), you can notify clients when resources change:

```kotlin
class MyService @Inject constructor(
  private val mcpServer: MiskMcpServer
) {
  
  suspend fun updateUserData() {
    // Update your data
    userRepository.updateUsers()
    
    // Notify subscribed MCP clients
    mcpServer.notifyUpdatedResource("schema://database/users")
    
    // Include metadata in the notification
    val metadata = buildJsonObject {
      put("lastModified", Clock.System.now().toString())
      put("recordCount", userRepository.count())
    }
    mcpServer.notifyUpdatedResource("schema://database/users", metadata)
  }
}
```

### Configuration for Resource Notifications

To enable resource update notifications, configure your server:

```yaml
mcp:
  my_server:
    resources:
      subscribe: true  # Required for notifications
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
    
    assertThat(result).isInstanceOf(McpTool.PromptToolResult::class.java)
    val promptResult = result as McpTool.PromptToolResult
    assertThat(promptResult.result).hasSize(1)
    assertThat((promptResult.result[0] as TextContent).text).isEqualTo("Result: 8.0")
    assertThat(promptResult.isError).isFalse()
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
    
    assertThat(result).isInstanceOf(StructuredMcpTool.StructuredToolResult::class.java)
    val structuredResult = result as StructuredMcpTool.StructuredToolResult<WeatherOutput>
    assertThat(structuredResult.result.city).isEqualTo("San Francisco")
    assertThat(structuredResult.result.temperature).isEqualTo(18.5)
    assertThat(structuredResult.result.humidity).isEqualTo(65)
    assertThat(structuredResult.isError).isFalse()
  }
}
```

## Learn More

- [MCP Specification](https://modelcontextprotocol.io/specification/2025-06-18) - Official protocol specification
- [MCP Examples](https://modelcontextprotocol.io/examples) - Example implementations
- [MCP SDKs](https://modelcontextprotocol.io) - Available SDKs for different languages
- [MCP Testing Tool](https://modelcontextprotocol.io/legacy/tools/inspector) - Local dashboard to test MCP server against your local running Misk service (simply run `$ npx @modelcontextprotocol/inspector`)
