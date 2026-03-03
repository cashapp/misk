package misk.web.marshal

import com.squareup.moshi.Moshi
import misk.exceptions.BadRequestException
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartReader
import okio.BufferedSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KType

object MultipartUnmarshaller : Unmarshaller {
  override fun unmarshal(
    requestHeaders: Headers,
    source: BufferedSource
  ): MultipartReader {
    val contentType = requestHeaders["Content-Type"]?.toMediaTypeOrNull()
        ?: throw BadRequestException("required content-type missing")
    val boundary = contentType.parameter("boundary")
        ?: throw BadRequestException("required boundary parameter missing")
    return MultipartReader(source, boundary)
  }

  @Singleton
  class Factory @Inject internal constructor(val moshi: Moshi) : Unmarshaller.Factory {
    override fun create(mediaType: MediaType, type: KType): Unmarshaller? {
      if (mediaType.type != "multipart") return null
      if (GenericUnmarshallers.canHandle(type)) return null
      return MultipartUnmarshaller
    }
  }
}
