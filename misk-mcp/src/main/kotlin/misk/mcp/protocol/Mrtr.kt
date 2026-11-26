@file:OptIn(ExperimentalSerializationApi::class)

package misk.mcp.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A request the server embeds in an [InputRequiredResult] for the client to fulfill before retrying. Only
 * [ElicitRequest] is supported: sampling and roots are deprecated.
 */
@Serializable(with = InputRequestSerializer::class)
sealed interface InputRequest {
  val method: String
}

/**
 * An interim result of a multi round-trip request (MRTR). The client fulfills [inputRequests] and retries the original
 * request with `inputResponses` under the same keys, echoing [requestState] unchanged.
 */
@Serializable
data class InputRequiredResult
@JvmOverloads
constructor(
  val inputRequests: Map<String, InputRequest>? = null,
  val requestState: String? = null,
  @SerialName("_meta") val meta: JsonObject? = null,
) {
  @EncodeDefault val resultType: ResultType = ResultType.InputRequired

  init {
    require(inputRequests != null || requestState != null) {
      "An InputRequiredResult needs inputRequests, requestState, or both"
    }
  }
}

internal object InputRequestSerializer : JsonContentPolymorphicSerializer<InputRequest>(InputRequest::class) {
  override fun selectDeserializer(element: JsonElement) =
    when (val method = element.jsonObject["method"]?.jsonPrimitive?.content) {
      McpMethod.ELICITATION_CREATE -> ElicitRequest.serializer()
      else -> throw SerializationException("Unsupported input request method: $method")
    }
}
