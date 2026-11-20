package misk.mcp.internal

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import misk.mcp.McpJson
import misk.mcp.protocol.JSONRPCMessage

/**
 * Lets misk's JSON unmarshaller decode the [JSONRPCMessage] request body of an MCP action, with [McpJson]. A Moshi
 * adapter, rather than an unmarshaller of its own, works whatever order the web and MCP modules are installed in.
 */
internal object McpJsonRpcMessageAdapter : JsonAdapter<JSONRPCMessage>() {
  override fun fromJson(reader: JsonReader): JSONRPCMessage =
    reader.nextSource().use { McpJson.decodeFromString(JSONRPCMessage.serializer(), it.readUtf8()) }

  override fun toJson(writer: JsonWriter, value: JSONRPCMessage?) {
    if (value == null) {
      writer.nullValue()
    } else {
      writer.valueSink().use { it.writeUtf8(McpJson.encodeToString(JSONRPCMessage.serializer(), value)) }
    }
  }
}
