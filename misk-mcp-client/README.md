# misk-mcp-client

This module provides Misk with Model Context Protocol (MCP) client capabilities, enabling your services to connect to and interact with MCP servers as a client.

## What is MCP?

The [Model Context Protocol](https://modelcontextprotocol.io/specification/2025-06-18) is an open protocol that enables seamless integration between LLM applications and external data sources and tools. MCP provides a standardized way for applications to:

- **Access contextual information** from external services through resources
- **Execute tools and capabilities** provided by remote MCP servers
- **Use reusable prompt templates** from external services
- **Build composable integrations** across the AI ecosystem

This client module allows your Misk application to act as an MCP client, connecting to external MCP servers to access their tools, resources, and prompts.

## Setup

Install the MCP client module in your Misk application:

```kotlin
@ExperimentalMiskApi
class MyAppModule : KAbstractModule() {
  override fun configure() {
    // Install the MCP client module with configuration
    install(McpClientModule.create(config.mcpClient))
    
    // Register your MCP client services
    install(McpClientServiceModule.create<MyMcpClientService>())
  }
}
```

## Configuration

Configure your MCP client connections in your application's YAML configuration:

```yaml
mcp_client:
  servers:
    weather_service:
      transport:
        type: "http"
        url: "https://weather-api.example.com/mcp"
        headers:
          Authorization: "Bearer ${WEATHER_API_TOKEN}"
      timeout_ms: 30000
      retry_policy:
        max_attempts: 3
        backoff_ms: 1000
    database_service:
      transport:
        type: "http" 
        url: "https://db-service.internal.com/mcp"
      timeout_ms: 10000
```

### Configuration Options

- **servers**: Map of server configurations by name
- **transport.type**: Transport type (currently only "http" supported)
- **transport.url**: MCP server endpoint URL
- **transport.headers**: Optional HTTP headers for authentication
- **timeout_ms**: Request timeout in milliseconds (default: 30000)
- **retry_policy**: Optional retry configuration
  - **max_attempts**: Maximum retry attempts (default: 1, no retries)
  - **backoff_ms**: Backoff delay between retries (default: 1000)

## Using MCP Clients

### Basic Client Usage

Inject and use MCP clients in your services:

```kotlin
@Singleton
class WeatherService @Inject constructor(
  @Named("weather_service") private val weatherClient: McpClient
) {
  
  suspend fun getCurrentWeather(city: String): WeatherData {
    // Call a tool on the remote MCP server
    val result = weatherClient.callTool(
      name = "get_current_weather",
      arguments = mapOf(
        "city" to city,
        "units" to "celsius"
      )
    )
    
    return when (result) {
      is McpToolResult.Success -> {
        // Parse the structured result
        Json.decodeFromString<WeatherData>(result.content)
      }
      is McpToolResult.Error -> {
        throw WeatherServiceException("Failed to get weather: ${result.message}")
      }
    }
  }
  
  suspend fun getWeatherSchema(): String {
    // Read a resource from the remote MCP server
    val result = weatherClient.readResource("schema://weather/current")
    
    return when (result) {
      is McpResourceResult.Success -> result.content
      is McpResourceResult.Error -> throw SchemaException("Failed to get schema: ${result.message}")
    }
  }
}
```

### Advanced Client Usage

For more complex scenarios, implement custom client services:

```kotlin
@Singleton
class DatabaseMcpService @Inject constructor(
  @Named("database_service") private val dbClient: McpClient,
  private val cacheService: CacheService
) {
  
  suspend fun executeQuery(query: String): QueryResult {
    // Check cache first
    val cacheKey = "query:${query.hashCode()}"
    cacheService.get(cacheKey)?.let { return it }
    
    // Execute query via MCP tool
    val result = dbClient.callTool(
      name = "execute_query",
      arguments = mapOf("sql" to query)
    )
    
    return when (result) {
      is McpToolResult.Success -> {
        val queryResult = Json.decodeFromString<QueryResult>(result.content)
        cacheService.put(cacheKey, queryResult, Duration.ofMinutes(5))
        queryResult
      }
      is McpToolResult.Error -> {
        throw DatabaseException("Query failed: ${result.message}")
      }
    }
  }
  
  suspend fun getTableSchema(tableName: String): TableSchema {
    val result = dbClient.readResource("schema://database/$tableName")
    
    return when (result) {
      is McpResourceResult.Success -> {
        Json.decodeFromString<TableSchema>(result.content)
      }
      is McpResourceResult.Error -> {
        throw SchemaException("Failed to get table schema: ${result.message}")
      }
    }
  }
  
  suspend fun generatePrompt(promptName: String, args: Map<String, String>): String {
    val result = dbClient.getPrompt(promptName, args)
    
    return when (result) {
      is McpPromptResult.Success -> result.messages.joinToString("\n") { it.content }
      is McpPromptResult.Error -> throw PromptException("Failed to get prompt: ${result.message}")
    }
  }
}
```

### Connection Management

The MCP client handles connection lifecycle automatically:

```kotlin
@Singleton
class McpHealthService @Inject constructor(
  private val mcpClientManager: McpClientManager
) {
  
  suspend fun checkServerHealth(): Map<String, ServerHealth> {
    return mcpClientManager.getAllClients().mapValues { (name, client) ->
      try {
        // Ping the server to check connectivity
        client.ping()
        ServerHealth.HEALTHY
      } catch (e: Exception) {
        logger.warn("MCP server $name is unhealthy", e)
        ServerHealth.UNHEALTHY
      }
    }
  }
  
  suspend fun reconnectServer(serverName: String) {
    mcpClientManager.reconnect(serverName)
  }
}
```

## Error Handling

Handle MCP client errors appropriately:

```kotlin
suspend fun safeToolCall(client: McpClient, toolName: String, args: Map<String, Any>): String? {
  return try {
    when (val result = client.callTool(toolName, args)) {
      is McpToolResult.Success -> result.content
      is McpToolResult.Error -> {
        logger.error("Tool call failed: ${result.message}")
        null
      }
    }
  } catch (e: McpConnectionException) {
    logger.error("Connection failed to MCP server", e)
    null
  } catch (e: McpTimeoutException) {
    logger.error("MCP request timed out", e)
    null
  } catch (e: Exception) {
    logger.error("Unexpected error during MCP call", e)
    null
  }
}
```

## Testing

Test your MCP client integrations:

```kotlin
@ExperimentalMiskApi
class WeatherServiceTest {
  
  @Test
  fun testGetCurrentWeather() {
    val mockClient = mockk<McpClient>()
    val weatherService = WeatherService(mockClient)
    
    // Mock successful tool call
    coEvery { 
      mockClient.callTool("get_current_weather", any()) 
    } returns McpToolResult.Success(
      content = """{"temperature": 22.5, "humidity": 65, "conditions": "sunny"}"""
    )
    
    val result = runBlocking { 
      weatherService.getCurrentWeather("San Francisco") 
    }
    
    assertEquals(22.5, result.temperature)
    assertEquals(65, result.humidity)
    assertEquals("sunny", result.conditions)
  }
  
  @Test
  fun testGetCurrentWeatherError() {
    val mockClient = mockk<McpClient>()
    val weatherService = WeatherService(mockClient)
    
    // Mock error response
    coEvery { 
      mockClient.callTool("get_current_weather", any()) 
    } returns McpToolResult.Error("City not found")
    
    assertFailsWith<WeatherServiceException> {
      runBlocking { weatherService.getCurrentWeather("InvalidCity") }
    }
  }
}
```

## Security Considerations

When using MCP clients:

1. **Authentication**: Use secure authentication methods (API keys, OAuth tokens)
2. **Input Validation**: Validate data received from external MCP servers
3. **Network Security**: Use HTTPS for all MCP connections
4. **Rate Limiting**: Implement appropriate rate limiting for external calls
5. **Error Handling**: Avoid exposing sensitive information in error messages
6. **Timeouts**: Configure appropriate timeouts to prevent hanging requests

## Learn More

- [MCP Specification](https://modelcontextprotocol.io/specification/2025-06-18) - Official protocol specification
- [MCP Examples](https://modelcontextprotocol.io/examples) - Example implementations
- [MCP SDKs](https://modelcontextprotocol.io) - Available SDKs for different languages
- [misk-mcp](../misk-mcp/README.md) - Server-side MCP implementation for Misk
