@file:OptIn(ExperimentalSerializationApi::class)

package misk.mcp.protocol

import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

const val JSONRPC_VERSION: String = "2.0"

/** A JSON-RPC request id: a string or an integer. Never null for MCP requests. */
@Serializable(with = RequestIdSerializer::class)
sealed interface RequestId {
  @JvmInline
  value class StringId(val value: String) : RequestId {
    override fun toString() = value
  }

  @JvmInline
  value class NumberId(val value: Long) : RequestId {
    override fun toString() = value.toString()
  }
}

fun RequestId(value: String): RequestId = RequestId.StringId(value)

fun RequestId(value: Long): RequestId = RequestId.NumberId(value)

typealias ProgressToken = RequestId

fun ProgressToken(value: String): ProgressToken = RequestId(value)

fun ProgressToken(value: Long): ProgressToken = RequestId(value)

/** Any message exchanged over JSON-RPC 2.0. */
@Serializable(with = JSONRPCMessageSerializer::class)
sealed interface JSONRPCMessage {
  val jsonrpc: String
}

@Serializable
data class JSONRPCRequest
@JvmOverloads
constructor(
  val id: RequestId = RequestId(UUID.randomUUID().toString()),
  val method: String,
  val params: JsonObject? = null,
) : JSONRPCMessage {
  @EncodeDefault override val jsonrpc: String = JSONRPC_VERSION

  constructor(id: String, method: String, params: JsonObject? = null) : this(RequestId(id), method, params)

  constructor(id: Long, method: String, params: JsonObject? = null) : this(RequestId(id), method, params)
}

@Serializable
data class JSONRPCNotification @JvmOverloads constructor(val method: String, val params: JsonObject? = null) :
  JSONRPCMessage {
  @EncodeDefault override val jsonrpc: String = JSONRPC_VERSION
}

@Serializable
data class JSONRPCResponse @JvmOverloads constructor(val id: RequestId, val result: JsonObject = EmptyJsonObject) :
  JSONRPCMessage {
  @EncodeDefault override val jsonrpc: String = JSONRPC_VERSION
}

@Serializable
data class JSONRPCError @JvmOverloads constructor(val id: RequestId? = null, val error: RPCError) : JSONRPCMessage {
  @EncodeDefault override val jsonrpc: String = JSONRPC_VERSION
}

@Serializable
data class RPCError @JvmOverloads constructor(val code: Int, val message: String, val data: JsonElement? = null) {
  object ErrorCode {
    const val PARSE_ERROR: Int = -32700
    const val INVALID_REQUEST: Int = -32600
    const val METHOD_NOT_FOUND: Int = -32601
    const val INVALID_PARAMS: Int = -32602
    const val INTERNAL_ERROR: Int = -32603

    /** Resource not found in protocol versions 2025-11-25 and earlier. Replaced by [INVALID_PARAMS]. */
    const val RESOURCE_NOT_FOUND: Int = -32002

    /** The HTTP headers do not match the request body, or a required header is missing or malformed. */
    const val HEADER_MISMATCH: Int = -32020

    /** The request needs a client capability that the client did not declare. */
    const val MISSING_REQUIRED_CLIENT_CAPABILITY: Int = -32021

    /** The server does not support the protocol version the request declared. */
    const val UNSUPPORTED_PROTOCOL_VERSION: Int = -32022
  }
}

/**
 * Thrown by a handler to fail a request with a specific JSON-RPC error. Any other exception becomes an
 * [RPCError.ErrorCode.INTERNAL_ERROR], except in tools, where it becomes a result with `isError: true`.
 */
class McpException
@JvmOverloads
constructor(
  val code: Int,
  message: String = "MCP error $code",
  val data: JsonElement? = null,
  cause: Throwable? = null,
) : Exception(message, cause)

internal object RequestIdSerializer : KSerializer<RequestId> {
  override val descriptor = PrimitiveSerialDescriptor("misk.mcp.protocol.RequestId", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): RequestId {
    val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return RequestId(decoder.decodeString())
    if (element !is JsonPrimitive) throw SerializationException("Request id must be a string or an integer")
    return if (element.isString) {
      RequestId(element.content)
    } else {
      RequestId(element.longOrNull ?: throw SerializationException("Request id must be a string or an integer"))
    }
  }

  override fun serialize(encoder: Encoder, value: RequestId) {
    val primitive =
      when (value) {
        is RequestId.StringId -> JsonPrimitive(value.value)
        is RequestId.NumberId -> JsonPrimitive(value.value)
      }
    if (encoder is JsonEncoder) {
      encoder.encodeJsonElement(primitive)
    } else {
      encoder.encodeString(primitive.content)
    }
  }
}

internal object JSONRPCMessageSerializer : JsonContentPolymorphicSerializer<JSONRPCMessage>(JSONRPCMessage::class) {
  override fun selectDeserializer(element: JsonElement) =
    when {
      element !is JsonObject -> throw SerializationException("A JSON-RPC message must be a JSON object")
      "method" in element -> if ("id" in element) JSONRPCRequest.serializer() else JSONRPCNotification.serializer()
      "error" in element -> JSONRPCError.serializer()
      "result" in element -> JSONRPCResponse.serializer()
      else -> throw SerializationException("Not a JSON-RPC request, notification, or response")
    }
}
