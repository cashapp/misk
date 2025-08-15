package misk.mcp.internal

import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class McpJsonRpcMessageUnmarshallerTest {

  @Test
  fun `test unmarshal JSON RPC message`() {
    // Create an instance of McpJsonRpcMessageUnmarshaller with McpJson
    val unmarshaller = McpJsonRpcMessageUnmarshaller(McpJson)

    // Create a valid JSON RPC request message
    val jsonRpcRequestJson = """
      {
        "jsonrpc": "2.0",
        "id": "test-123",
        "method": "tools/list",
        "params": {}
      }
    """.trimIndent()

    // Create a BufferedSource from the JSON string
    val buffer = Buffer().writeUtf8(jsonRpcRequestJson)
    val headers = Headers.Builder().build()

    // Unmarshal the message
    val result = unmarshaller.unmarshal(headers, buffer)

    // Verify that the result is a JSONRPCMessage (the unmarshaller returns the actual concrete type)
    assertNotNull(result)
  }

  @Test
  fun `test unmarshal JSON RPC notification message`() {
    // Create an instance of McpJsonRpcMessageUnmarshaller with McpJson
    val unmarshaller = McpJsonRpcMessageUnmarshaller(McpJson)

    // Create a valid JSON RPC notification message (no id field)
    val jsonRpcNotificationJson = """
      {
        "jsonrpc": "2.0",
        "method": "notifications/resources/updated",
        "params": {
          "uri": "schema://database/users"
        }
      }
    """.trimIndent()

    // Create a BufferedSource from the JSON string
    val buffer = Buffer().writeUtf8(jsonRpcNotificationJson)
    val headers = Headers.Builder().build()

    // Unmarshal the message
    val result = unmarshaller.unmarshal(headers, buffer)

    // Verify that the result is a JSONRPCMessage (the unmarshaller returns the actual concrete type)
    assertNotNull(result)
  }

  @Test
  fun `test unmarshal JSON RPC response message`() {
    // Create an instance of McpJsonRpcMessageUnmarshaller with McpJson
    val unmarshaller = McpJsonRpcMessageUnmarshaller(McpJson)

    // Create a valid JSON RPC response message
    val jsonRpcResponseJson = """
      {
        "jsonrpc": "2.0",
        "id": "test-456",
        "result": {
          "tools": []
        }
      }
    """.trimIndent()

    // Create a BufferedSource from the JSON string
    val buffer = Buffer().writeUtf8(jsonRpcResponseJson)
    val headers = Headers.Builder().build()

    // Unmarshal the message
    val result = unmarshaller.unmarshal(headers, buffer)

    // Verify that the result is a JSONRPCMessage (the unmarshaller returns the actual concrete type)
    assertNotNull(result)
  }

  @Test
  fun `test create unmarshaller`() {
    // Create an instance of McpJsonRpcMessageUnmarshaller.Factory
    val factory = McpJsonRpcMessageUnmarshaller.Factory(McpJson)

    // Test with correct media type and type
    val applicationJsonMediaType = MediaTypes.APPLICATION_JSON_MEDIA_TYPE
    val jsonRpcMessageType = JSONRPCMessage::class.createType()

    // Verify that it creates an unmarshaller for JSON RPC messages
    val unmarshaller = factory.create(applicationJsonMediaType, jsonRpcMessageType)

    assertNotNull(unmarshaller)
    assertEquals(unmarshaller::class, McpJsonRpcMessageUnmarshaller::class)
  }

  @Test
  fun `test unmarshaller with invalid media type`() {
    // Create an instance of McpJsonRpcMessageUnmarshaller.Factory
    val factory = McpJsonRpcMessageUnmarshaller.Factory(McpJson)

    // Test with unsupported media types
    val textPlainMediaType = "text/plain".toMediaType()
    val xmlMediaType = "application/xml".toMediaType()
    val jsonRpcMessageType = JSONRPCMessage::class.createType()

    // Verify that the unmarshaller returns null for unsupported media types
    assertNull(factory.create(textPlainMediaType, jsonRpcMessageType))
    assertNull(factory.create(xmlMediaType, jsonRpcMessageType))
  }

  @Test
  fun `test unmarshaller with invalid type`() {
    // Create an instance of McpJsonRpcMessageUnmarshaller.Factory
    val factory = McpJsonRpcMessageUnmarshaller.Factory(McpJson)

    // Test with correct media type but wrong types
    val applicationJsonMediaType = MediaTypes.APPLICATION_JSON_MEDIA_TYPE
    val stringType = typeOf<String>()
    val intType = typeOf<Int>()
    val mapType = typeOf<Map<String, Any>>()

    // Verify that the unmarshaller returns null for unsupported types
    assertNull(factory.create(applicationJsonMediaType, stringType))
    assertNull(factory.create(applicationJsonMediaType, intType))
    assertNull(factory.create(applicationJsonMediaType, mapType))
  }

  @Test
  fun `test unmarshaller with application json subtype variations`() {
    // Create an instance of McpJsonRpcMessageUnmarshaller.Factory
    val factory = McpJsonRpcMessageUnmarshaller.Factory(McpJson)

    // Test with different JSON media type variations
    val jsonUtf8MediaType = "application/json; charset=utf-8".toMediaType()
    val jsonRpcMessageType = JSONRPCMessage::class.createType()

    // Should work with charset parameter
    val unmarshaller = factory.create(jsonUtf8MediaType, jsonRpcMessageType)
    assertNotNull(unmarshaller)
    assertEquals(unmarshaller::class, McpJsonRpcMessageUnmarshaller::class)
  }

}
