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

Tools are functions that AI models can execute. Implement the `McpTool` interface:

```kotlin
@Singleton
class CalculatorTool @Inject constructor() : McpTool<Input>(Input::class) {
  override val name = "calculator"
  override val description = "Performs basic arithmetic operations"

  @Serializable
  data class Input(
    @Description("The mathematical operator (\"add\", \"subtract\", \"multiply\", or \"divide\") to execute")
    val operator: String

    @Description("The first operand in the operation (a number)")
    val a: Number

    @Description("The second operand in the operation (a number)")
    val b: Number
  )
  
  override suspend fun handle(request: CallToolRequest): CallToolResult {
    val input = request.parseInput<Input>()
    val aDouble = a.toDouble()
    val bDouble = b.toDouble()
    
    val result = when (input.operator) {
      "add" -> aDouble + bDouble
      "subtract" -> aDouble - bDouble
      "multiply" -> aDouble * bDouble
      "divide" -> if (bDouble != 0.0) aDouble / bDouble else throw IllegalArgumentException("Division by zero")
      else -> throw IllegalArgumentException("Unknown operator: $operator")
    }
    
    return CallToolResult(
      content = listOf(
        TextContent(text = "Result: $result")
      )
    )
  }
}
```

Register the tool:

```kotlin
install(McpToolModule.create<CalculatorTool>())
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

Use the fake implementations for testing:

```kotlin
class MyMcpTest {
  @Test
  fun testCalculatorTool() {
    val tool = CalculatorTool()
    val request = CallToolRequest(
      params = CallToolParams(
        name = "calculator",
        arguments = mapOf(
          "operation" to "add",
          "a" to 5,
          "b" to 3
        )
      )
    )
    
    val result = runBlocking { tool.handler(request) }
    
    assertThat(result.content).hasSize(1)
    assertThat((result.content[0] as TextContent).text).isEqualTo("Result: 8.0")
  }
}
```

## Learn More

- [MCP Specification](https://modelcontextprotocol.io/specification/2025-06-18) - Official protocol specification
- [MCP Examples](https://modelcontextprotocol.io/examples) - Example implementations
- [MCP SDKs](https://modelcontextprotocol.io) - Available SDKs for different languages
