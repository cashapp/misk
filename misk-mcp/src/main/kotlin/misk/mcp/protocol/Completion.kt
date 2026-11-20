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

@Serializable
data class CompleteRequest(val params: CompleteRequestParams) {
  val method: String
    get() = McpMethod.COMPLETION_COMPLETE

  val ref: Reference
    get() = params.ref

  val argument: CompleteRequestParams.Argument
    get() = params.argument

  val context: CompleteRequestParams.Context?
    get() = params.context

  val meta: RequestMeta?
    get() = params.meta
}

@Serializable
data class CompleteRequestParams
@JvmOverloads
constructor(
  val argument: Argument,
  val ref: Reference,
  val context: Context? = null,
  @SerialName("_meta") val meta: RequestMeta? = null,
) {
  @Serializable data class Argument(val name: String, val value: String)

  @Serializable data class Context @JvmOverloads constructor(val arguments: Map<String, String>? = null)
}

/** What a completion request completes: a prompt argument or a resource template variable. */
@Serializable(with = ReferenceSerializer::class)
sealed interface Reference {
  val type: String
}

@Serializable
data class PromptReference @JvmOverloads constructor(val name: String, val title: String? = null) : Reference {
  @EncodeDefault override val type: String = TYPE

  companion object {
    const val TYPE = "ref/prompt"
  }
}

@Serializable
data class ResourceTemplateReference(val uri: String) : Reference {
  @EncodeDefault override val type: String = TYPE

  companion object {
    const val TYPE = "ref/resource"
  }
}

@Serializable
data class CompleteResult
@JvmOverloads
constructor(val completion: Completion, @SerialName("_meta") val meta: JsonObject? = null) {
  @Serializable
  data class Completion
  @JvmOverloads
  constructor(val values: List<String>, val total: Int? = null, val hasMore: Boolean? = null) {
    init {
      require(values.size <= MAX_VALUES) { "Completion 'values' must not exceed $MAX_VALUES items, got ${values.size}" }
    }

    companion object {
      const val MAX_VALUES = 100
    }
  }
}

internal object ReferenceSerializer : JsonContentPolymorphicSerializer<Reference>(Reference::class) {
  override fun selectDeserializer(element: JsonElement) =
    when (val type = element.jsonObject["type"]?.jsonPrimitive?.content) {
      PromptReference.TYPE -> PromptReference.serializer()
      ResourceTemplateReference.TYPE -> ResourceTemplateReference.serializer()
      else -> throw SerializationException("Unknown reference type: $type")
    }
}
