package misk.mcp.internal

import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.serialization.json.Json
import misk.web.marshal.Unmarshaller
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import okhttp3.MediaType
import okio.BufferedSource
import kotlin.reflect.KType
import kotlin.reflect.full.createType

internal class McpJsonRpcMessageUnmarshaller(private val json: Json) : Unmarshaller {
  override fun unmarshal(requestHeaders: Headers, source: BufferedSource) =
    json.decodeFromString<JSONRPCMessage>(source.readUtf8())

  @Singleton
  class Factory @Inject internal constructor(@MiskMcp val json: Json) : Unmarshaller.Factory {
    override fun create(mediaType: MediaType, type: KType): Unmarshaller? {
      if (mediaType.type != MediaTypes.APPLICATION_JSON_MEDIA_TYPE.type ||
        mediaType.subtype != MediaTypes.APPLICATION_JSON_MEDIA_TYPE.subtype
      ) return null

      if (type != JSONRPCMessage::class.createType()) return null

      return McpJsonRpcMessageUnmarshaller(json)
    }
  }
}
