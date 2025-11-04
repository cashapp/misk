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
@ExperimentalMiskApi
class MyAppModule : KAbstractModule() {
  override fun configure() {
    // Install the MCP server module with configuration
    install(McpServerModule.create("my_server", config.mcp))
    
    // Register your MCP components
    install(McpToolModule.create<CalculatorTool>())
    install(McpResourceModule.create<DatabaseSchemaResource>())
    install(McpPromptModule.create<CodeReviewPrompt>())
  }
}
```

## Web Action Integration

To expose MCP functionality through HTTP endpoints, you need to create web actions using the MCP annotations and install the appropriate WebActionModule. The misk-mcp module supports two transport protocols:

### Transport Options

#### StreamableHTTP Transport
Uses HTTP POST requests with Server-Sent Events (SSE) for streaming responses:
- **`@McpPost`** (Required): Handles incoming MCP requests from clients
- **`@McpGet`** (Optional): Enables out-of-band server-to-client notifications
- **`@McpDelete`** (Optional): Allows clients to explicitly delete an existing stateful session

#### WebSocket Transport  
Uses persistent WebSocket connections for full bidirectional communication:
- **`@McpWebSocket`** (Required): Handles all MCP communication over WebSocket

### StreamableHTTP Transport Example

```kotlin
@ExperimentalMiskApi
@Singleton
class MyMcpSseAction @Inject constructor(
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

  @McpGet  
  suspend fun streamServerEvents(
    sendChannel: SendChannel<ServerSentEvent>
  ) {
    mcpStreamManager.withSseChannel(sendChannel) {
      // Stream server-initiated events to client
    }
  }

  @McpDelete
  suspend fun terminateSession(
    @RequestHeaders headers: Headers,
    sendChannel: SendChannel<ServerSentEvent>
  ) {
    val sessionId = headers[SESSION_ID_HEADER] ?: throw IllegalArgumentException("Missing session ID")
    // Any session state should be cleaned up here
  }
}
```

### WebSocket Transport Example

```kotlin
@ExperimentalMiskApi
@Singleton
class MyMcpWebSocketAction @Inject constructor(
  private val mcpStreamManager: McpStreamManager
) : WebAction {

  @McpWebSocket
  fun handleWebSocket(webSocket: WebSocket): WebSocketListener {
    return mcpStreamManager.withWebSocket(webSocket)
  }
}
```

### Transport Comparison

| Feature | StreamableHTTP Transport | WebSocket Transport |
|---------|--------------------------|-------------------|
| **Connection Type** | HTTP POST + SSE streaming | Persistent WebSocket |
| **Communication** | Client→Server (POST)<br/>Server→Client (SSE) | Full bidirectional |
| **Complexity** | Multiple endpoints | Single endpoint |
| **Session Management** | Optional via headers | Built-in connection state |
| **Use Cases** | Traditional web apps<br/>Request-response patterns | Real-time applications<br/>Interactive AI tools |
| **Client Support** | Universal HTTP support | WebSocket support required |

### Choosing a Transport

**Use StreamableHTTP Transport when:**
- Building traditional web applications
- Need maximum client compatibility
- Implementing request-response patterns
- Want explicit control over session management

**Use WebSocket Transport when:**
- Building real-time interactive applications
- Need bidirectional communication
- Want simplified connection management
- Implementing conversational AI interfaces

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

Without the WebActionModule installation, your `@McpPost`, `@McpGet`, and `@McpDelete` annotated methods will not be registered as HTTP endpoints.

## Stateful Sessions (Optional)

The MCP server supports stateful sessions using the `Mcp-Session-Id` header. This is optional and allows clients (like LLMs) to maintain context across multiple requests.

```kotlin
const val SESSION_ID_HEADER = "Mcp-Session-Id"
```

When a client includes the `Mcp-Session-Id` header in requests:
- The server maintains session state between requests
- Server-to-client notifications can be sent via the `@McpGet` endpoint
- Sessions can be explicitly terminated using the `@McpDelete` endpoint

If no session ID is provided, the server operates in a stateless mode where each request is independent.

## Session Management

The MCP server provides comprehensive session management capabilities following the [MCP specification section 2.5](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#session-management). Sessions enable stateful interactions between clients and servers, allowing for context preservation across multiple requests.

### Installing Session Management

To enable session management, implement the `McpSessionHandler` interface and install it using `McpSessionHandlerModule`:

```kotlin
@Singleton
class RedisSessionHandler @Inject constructor(
  private val redis: RedisClient
) : McpSessionHandler {
  
  override suspend fun initialize(): String {
    // Generate a unique, cryptographically secure session ID
    val sessionId = UUID.randomUUID().toString()
    redis.setex("session:$sessionId", 3600, "active") // 1 hour expiry
    return sessionId
  }
  
  override suspend fun isActive(sessionId: String): Boolean {
    return redis.exists("session:$sessionId")
  }
  
  override suspend fun terminate(sessionId: String) {
    redis.del("session:$sessionId")
    // Clean up any session-related resources
  }
}
```

Install the session handler in your module:

```kotlin
@ExperimentalMiskApi
class MyAppModule : KAbstractModule() {
  override fun configure() {
    // Install MCP server module
    install(McpServerModule.create("my_server", config.mcp))
    
    // Install session handler
    install(McpSessionHandlerModule.create<RedisSessionHandler>())
    
    // Install other MCP components
    install(McpToolModule.create<MyTool>())
  }
}
```

### Framework Integration

When a session handler is installed, the framework automatically:

- Calls `initialize()` when a new session is needed
- Calls `isActive()` to validate existing sessions before processing requests
- Includes the session ID in the `Mcp-Session-Id` response header
- Manages session lifecycle across requests

### Accessing Session IDs in Tools

Use `McpSessionId` to access the current session ID within your MCP tools:

```kotlin
@ExperimentalMiskApi
@Singleton
class SessionAwareTool @Inject constructor(
  private val sessionId: Provider<McpSessionId>,
  private val userService: UserService
) : McpTool<UserDataInput>() {
  
  override val name = "get_user_data"
  override val description = "Retrieves user data for the current session"
  
  override suspend fun handle(input: UserDataInput): ToolResult {
    val currentSessionId = sessionId.get().get()
    val userData = userService.getUserDataForSession(currentSessionId)
    
    return ToolResult(
      TextContent("User data: ${userData.name} (Session: $currentSessionId)")
    )
  }
}
```

### Session Termination

Sessions can be terminated in several ways:

#### Client-Directed Termination

Clients can terminate sessions using the `@McpDelete` endpoint:

```kotlin
@McpDelete
suspend fun terminateSession(
  @RequestHeaders headers: Headers,
  sendChannel: SendChannel<ServerSentEvent>
) {
  val sessionId = headers[SESSION_ID_HEADER] ?: throw IllegalArgumentException("Missing session ID")
  
  // Session handler will be called automatically to clean up
  // Additional cleanup can be performed here if needed
}
```

#### Programmatic Termination

Server code can terminate sessions directly:

```kotlin
@Singleton
class MyService @Inject constructor(
  private val sessionHandler: McpSessionHandler
) {
  
  suspend fun cleanupExpiredSessions() {
    val expiredSessions = findExpiredSessions()
    expiredSessions.forEach { sessionId ->
      sessionHandler.terminate(sessionId)
    }
  }
}
```

### Session Storage Examples


#### Database Session Handler

```kotlin
@Singleton
class DatabaseSessionHandler @Inject constructor(
  private val sessionDao: SessionDao
) : McpSessionHandler {
  
  override suspend fun initialize(): String {
    val sessionId = UUID.randomUUID().toString()
    sessionDao.createSession(
      Session(
        id = sessionId,
        createdAt = Clock.System.now(),
        expiresAt = Clock.System.now().plus(1.hours)
      )
    )
    return sessionId
  }
  
  override suspend fun isActive(sessionId: String): Boolean {
    val session = sessionDao.findById(sessionId) ?: return false
    return session.expiresAt > Clock.System.now()
  }
  
  override suspend fun terminate(sessionId: String) {
    sessionDao.deleteById(sessionId)
  }
}
```

### Multi-Tenant Session Management

For multi-tenant applications, use annotation-based session handlers:

```kotlin
// Define tenant-specific annotations
@Qualifier
annotation class TenantA

@Qualifier  
annotation class TenantB

// Install tenant-specific session handlers
install(McpSessionHandlerModule.create<TenantASessionHandler>(TenantA::class))
install(McpSessionHandlerModule.create<TenantBSessionHandler>(TenantB::class))
```

### Security Considerations

When implementing session management:

- **Unique Session IDs**: Use cryptographically secure random session identifiers
- **Session Expiration**: Implement appropriate session timeouts
- **Concurrent Access**: Ensure thread-safe session storage and access
- **Cleanup**: Properly clean up session resources to prevent memory leaks
- **Validation**: Always validate session IDs before processing requests

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
