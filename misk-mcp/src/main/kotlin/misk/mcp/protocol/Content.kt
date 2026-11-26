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

/** Content in a tool result or prompt message. Each subtype serializes its `type` discriminator. */
@Serializable(with = ContentBlockSerializer::class)
sealed interface ContentBlock {
  val type: String
  val meta: JsonObject?
}

@Serializable
data class TextContent
@JvmOverloads
constructor(
  val text: String,
  val annotations: Annotations? = null,
  @SerialName("_meta") override val meta: JsonObject? = null,
) : ContentBlock {
  @EncodeDefault override val type: String = TYPE

  companion object {
    const val TYPE = "text"
  }
}

@Serializable
data class ImageContent
@JvmOverloads
constructor(
  val data: String,
  val mimeType: String,
  val annotations: Annotations? = null,
  @SerialName("_meta") override val meta: JsonObject? = null,
) : ContentBlock {
  @EncodeDefault override val type: String = TYPE

  companion object {
    const val TYPE = "image"
  }
}

@Serializable
data class AudioContent
@JvmOverloads
constructor(
  val data: String,
  val mimeType: String,
  val annotations: Annotations? = null,
  @SerialName("_meta") override val meta: JsonObject? = null,
) : ContentBlock {
  @EncodeDefault override val type: String = TYPE

  companion object {
    const val TYPE = "audio"
  }
}

@Serializable
data class ResourceLink
@JvmOverloads
constructor(
  val name: String,
  val uri: String,
  val title: String? = null,
  val size: Long? = null,
  val mimeType: String? = null,
  val icons: List<Icon>? = null,
  val description: String? = null,
  val annotations: Annotations? = null,
  @SerialName("_meta") override val meta: JsonObject? = null,
) : ContentBlock {
  @EncodeDefault override val type: String = TYPE

  companion object {
    const val TYPE = "resource_link"
  }
}

@Serializable
data class EmbeddedResource
@JvmOverloads
constructor(
  val resource: ResourceContents,
  val annotations: Annotations? = null,
  @SerialName("_meta") override val meta: JsonObject? = null,
) : ContentBlock {
  @EncodeDefault override val type: String = TYPE

  companion object {
    const val TYPE = "resource"
  }
}

/** The contents of a resource: text or base64-encoded binary data. */
@Serializable(with = ResourceContentsSerializer::class)
sealed interface ResourceContents {
  val uri: String
  val mimeType: String?
  val meta: JsonObject?
}

@Serializable
data class TextResourceContents
@JvmOverloads
constructor(
  val text: String,
  override val uri: String,
  override val mimeType: String? = null,
  @SerialName("_meta") override val meta: JsonObject? = null,
) : ResourceContents

@Serializable
data class BlobResourceContents
@JvmOverloads
constructor(
  val blob: String,
  override val uri: String,
  override val mimeType: String? = null,
  @SerialName("_meta") override val meta: JsonObject? = null,
) : ResourceContents

internal object ContentBlockSerializer : JsonContentPolymorphicSerializer<ContentBlock>(ContentBlock::class) {
  override fun selectDeserializer(element: JsonElement) =
    when (val type = element.jsonObject["type"]?.jsonPrimitive?.content) {
      TextContent.TYPE -> TextContent.serializer()
      ImageContent.TYPE -> ImageContent.serializer()
      AudioContent.TYPE -> AudioContent.serializer()
      ResourceLink.TYPE -> ResourceLink.serializer()
      EmbeddedResource.TYPE -> EmbeddedResource.serializer()
      else -> throw SerializationException("Unknown content type: $type")
    }
}

internal object ResourceContentsSerializer :
  JsonContentPolymorphicSerializer<ResourceContents>(ResourceContents::class) {
  override fun selectDeserializer(element: JsonElement) =
    when {
      "text" in element.jsonObject -> TextResourceContents.serializer()
      "blob" in element.jsonObject -> BlobResourceContents.serializer()
      else -> throw SerializationException("Resource contents must have 'text' or 'blob'")
    }
}
