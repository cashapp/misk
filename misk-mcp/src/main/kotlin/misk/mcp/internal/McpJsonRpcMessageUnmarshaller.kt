package misk.mcp.internal

import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import misk.mcp.decode
import misk.web.marshal.Unmarshaller
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import okhttp3.MediaType
import okio.BufferedSource

internal class McpJsonRpcMessageUnmarshaller() : Unmarshaller {
  override fun unmarshal(requestHeaders: Headers, source: BufferedSource) = source.readUtf8().decode<JSONRPCMessage>()

  @Singleton
  class Factory @Inject internal constructor() : Unmarshaller.Factory {
    override fun create(mediaType: MediaType, type: KType): Unmarshaller? {
      if (
        mediaType.type != MediaTypes.APPLICATION_JSON_MEDIA_TYPE.type ||
          mediaType.subtype != MediaTypes.APPLICATION_JSON_MEDIA_TYPE.subtype
      )
        return null

      if (type != JSONRPCMessage::class.createType()) return null

      return McpJsonRpcMessageUnmarshaller()
    }
  }
}
