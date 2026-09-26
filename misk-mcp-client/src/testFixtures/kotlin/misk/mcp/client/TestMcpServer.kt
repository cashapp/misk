package misk.mcp.client

import kotlinx.serialization.json.*

/**
 * Mock MCP server for testing client functionality.
 * 
 * Provides a simple in-memory implementation that can be used
 * in tests to verify client behavior without requiring a real
 * MCP server.
 */
class TestMcpServer {
  
  private val tools = mutableMapOf<String, TestTool>()
  private val resources = mutableMapOf<String, TestResource>()
  private val prompts = mutableMapOf<String, TestPrompt>()
  
  /**
   * Register a test tool.
   */
  fun registerTool(name: String, description: String, handler: (Map<String, Any>) -> String) {
    tools[name] = TestTool(name, description, handler)
  }
  
  /**
   * Register a test resource.
   */
  fun registerResource(uri: String, name: String, content: String, mimeType: String? = null) {
    resources[uri] = TestResource(uri, name, content, mimeType)
  }
  
  /**
   * Register a test prompt.
   */
  fun registerPrompt(name: String, description: String, handler: (Map<String, String>) -> List<McpPromptMessage>) {
    prompts[name] = TestPrompt(name, description, handler)
  }
  
  /**
   * Handle a JSON-RPC request and return a response.
   */
  fun handleRequest(request: JsonObject): JsonObject {
    val method = request["method"]?.jsonPrimitive?.content
    val params = request["params"]?.jsonObject
    val id = request["id"]
    
    return when (method) {
      "tools/call" -> handleToolCall(params, id)
      "tools/list" -> handleToolsList(id)
      "resources/read" -> handleResourceRead(params, id)
      "resources/list" -> handleResourcesList(id)
      "prompts/get" -> handlePromptGet(params, id)
      "prompts/list" -> handlePromptsList(id)
      "ping" -> handlePing(id)
      else -> createErrorResponse(id, "Method not found", -32601)
    }
  }
  
  private fun handleToolCall(params: JsonObject?, id: JsonElement?): JsonObject {
    val toolName = params?.get("name")?.jsonPrimitive?.content
    val arguments = params?.get("arguments")?.jsonObject?.let { args ->
      args.mapValues { (_, value) ->
        when (value) {
          is JsonPrimitive -> value.content
          else -> value.toString()
        }
      }
    } ?: emptyMap()
    
    val tool = tools[toolName]
    return if (tool != null) {
      try {
        val result = tool.handler(arguments)
        JsonObject(mapOf(
          "jsonrpc" to JsonPrimitive("2.0"),
          "id" to (id ?: JsonNull),
          "result" to JsonObject(mapOf(
            "content" to JsonArray(listOf(
              JsonObject(mapOf(
                "type" to JsonPrimitive("text"),
                "text" to JsonPrimitive(result)
              ))
            ))
          ))
        ))
      } catch (e: Exception) {
        createErrorResponse(id, "Tool execution failed: ${e.message}", -32000)
      }
    } else {
      createErrorResponse(id, "Tool not found: $toolName", -32602)
    }
  }
  
  private fun handleToolsList(id: JsonElement?): JsonObject {
    val toolsList = tools.values.map { tool ->
      JsonObject(mapOf(
        "name" to JsonPrimitive(tool.name),
        "description" to JsonPrimitive(tool.description)
      ))
    }
    
    return JsonObject(mapOf(
      "jsonrpc" to JsonPrimitive("2.0"),
      "id" to (id ?: JsonNull),
      "result" to JsonObject(mapOf(
        "tools" to JsonArray(toolsList)
      ))
    ))
  }
  
  private fun handleResourceRead(params: JsonObject?, id: JsonElement?): JsonObject {
    val uri = params?.get("uri")?.jsonPrimitive?.content
    val resource = resources[uri]
    
    return if (resource != null) {
      JsonObject(mapOf(
        "jsonrpc" to JsonPrimitive("2.0"),
        "id" to (id ?: JsonNull),
        "result" to JsonObject(mapOf(
          "contents" to JsonArray(listOf(
            JsonObject(buildMap {
              put("uri", JsonPrimitive(resource.uri))
              put("text", JsonPrimitive(resource.content))
              resource.mimeType?.let { put("mimeType", JsonPrimitive(it)) }
            })
          ))
        ))
      ))
    } else {
      createErrorResponse(id, "Resource not found: $uri", -32602)
    }
  }
  
  private fun handleResourcesList(id: JsonElement?): JsonObject {
    val resourcesList = resources.values.map { resource ->
      JsonObject(buildMap {
        put("uri", JsonPrimitive(resource.uri))
        put("name", JsonPrimitive(resource.name))
        resource.mimeType?.let { put("mimeType", JsonPrimitive(it)) }
      })
    }
    
    return JsonObject(mapOf(
      "jsonrpc" to JsonPrimitive("2.0"),
      "id" to (id ?: JsonNull),
      "result" to JsonObject(mapOf(
        "resources" to JsonArray(resourcesList)
      ))
    ))
  }
  
  private fun handlePromptGet(params: JsonObject?, id: JsonElement?): JsonObject {
    val promptName = params?.get("name")?.jsonPrimitive?.content
    val arguments = params?.get("arguments")?.jsonObject?.mapValues { (_, value) ->
      value.jsonPrimitive.content
    } ?: emptyMap()
    
    val prompt = prompts[promptName]
    return if (prompt != null) {
      try {
        val messages = prompt.handler(arguments)
        val messagesList = messages.map { message ->
          JsonObject(mapOf(
            "role" to JsonPrimitive(message.role),
            "content" to JsonObject(mapOf(
              "type" to JsonPrimitive("text"),
              "text" to JsonPrimitive(message.content)
            ))
          ))
        }
        
        JsonObject(mapOf(
          "jsonrpc" to JsonPrimitive("2.0"),
          "id" to (id ?: JsonNull),
          "result" to JsonObject(mapOf(
            "messages" to JsonArray(messagesList)
          ))
        ))
      } catch (e: Exception) {
        createErrorResponse(id, "Prompt execution failed: ${e.message}", -32000)
      }
    } else {
      createErrorResponse(id, "Prompt not found: $promptName", -32602)
    }
  }
  
  private fun handlePromptsList(id: JsonElement?): JsonObject {
    val promptsList = prompts.values.map { prompt ->
      JsonObject(mapOf(
        "name" to JsonPrimitive(prompt.name),
        "description" to JsonPrimitive(prompt.description)
      ))
    }
    
    return JsonObject(mapOf(
      "jsonrpc" to JsonPrimitive("2.0"),
      "id" to (id ?: JsonNull),
      "result" to JsonObject(mapOf(
        "prompts" to JsonArray(promptsList)
      ))
    ))
  }
  
  private fun handlePing(id: JsonElement?): JsonObject {
    return JsonObject(mapOf(
      "jsonrpc" to JsonPrimitive("2.0"),
      "id" to (id ?: JsonNull),
      "result" to JsonObject(emptyMap())
    ))
  }
  
  private fun createErrorResponse(id: JsonElement?, message: String, code: Int): JsonObject {
    return JsonObject(mapOf(
      "jsonrpc" to JsonPrimitive("2.0"),
      "id" to (id ?: JsonNull),
      "error" to JsonObject(mapOf(
        "code" to JsonPrimitive(code),
        "message" to JsonPrimitive(message)
      ))
    ))
  }
  
  private data class TestTool(
    val name: String,
    val description: String,
    val handler: (Map<String, Any>) -> String
  )
  
  private data class TestResource(
    val uri: String,
    val name: String,
    val content: String,
    val mimeType: String?
  )
  
  private data class TestPrompt(
    val name: String,
    val description: String,
    val handler: (Map<String, String>) -> List<McpPromptMessage>
  )
}
