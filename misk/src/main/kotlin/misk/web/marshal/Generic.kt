package misk.web.marshal

import misk.inject.typeLiteral
import misk.web.ResponseBody
import misk.web.marshal.Marshaller.Companion.actualResponseType
import okhttp3.Headers
import okhttp3.MediaType
import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString
import java.lang.reflect.Type
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

/** Handles generic unmarshalling, for cases where the action can accept anything */
object GenericUnmarshallers {
  fun canHandle(type: KType) = canHandle(type.javaType)
  fun canHandle(type: Type) = GENERIC_REQUEST_TYPES.contains(type)

  private val GENERIC_REQUEST_TYPES = setOf(
    String::class.java,
    BufferedSource::class.java,
    ByteString::class.java
  )

  private object ToString : Unmarshaller {
    override fun unmarshal(requestHeaders: Headers, source: BufferedSource): Any? =
      source.readUtf8()
  }

  private object ToBufferedSource : Unmarshaller {
    override fun unmarshal(requestHeaders: Headers, source: BufferedSource): Any? = source
  }

  private object ToByteString : Unmarshaller {
    override fun unmarshal(requestHeaders: Headers, source: BufferedSource): Any? =
      source.readByteString()
  }

  fun into(parameter: KParameter): Unmarshaller? {
    val paramType = parameter.type
    return when (paramType.typeLiteral().rawType) {
      String::class.java -> ToString
      BufferedSource::class.java -> ToBufferedSource
      ByteString::class.java -> ToByteString
      else -> null
    }
  }
}

/** Handles generic marshalling, for cases where the action doesn't explicitly specify return content */
object GenericMarshallers {
  fun canHandle(type: KType) = canHandle(type.javaType)
  fun canHandle(type: Type) = GENERIC_RESPONSE_TYPES.contains(type)

  private val GENERIC_RESPONSE_TYPES = setOf(
    String::class.java,
    ResponseBody::class.java,
    ByteString::class.java,
    Nothing::class.java
  )

  private class FromResponseBody(private val contentType: MediaType?) : Marshaller<ResponseBody> {
    override fun contentType(): MediaType? = contentType
    override fun responseBody(o: ResponseBody) = o
  }

  private class FromString(private val contentType: MediaType?) : Marshaller<String> {
    override fun contentType(): MediaType? = contentType
    override fun responseBody(o: String) = object : ResponseBody {
      override fun writeTo(sink: BufferedSink) {
        sink.writeString(o, Charsets.UTF_8)
      }
    }
  }

  class FromByteString(private val contentType: MediaType?) : Marshaller<ByteString> {
    override fun contentType(): MediaType? = contentType
    override fun responseBody(o: ByteString) = object : ResponseBody {
      override fun writeTo(sink: BufferedSink) {
        sink.write(o)
      }
    }
  }

  class ToNothing(private val contentType: MediaType?) : Marshaller<Nothing> {
    override fun contentType(): MediaType? = contentType
    override fun responseBody(o: Nothing) = object : ResponseBody {
      override fun writeTo(sink: BufferedSink) {}
    }
  }

  fun from(contentType: MediaType?, returnType: KType): Marshaller<Any>? {
    @Suppress("UNCHECKED_CAST")
    return when (actualResponseType(returnType)) {
      String::class.java -> FromString(contentType)
      ByteString::class.java -> FromByteString(contentType)
      ResponseBody::class.java -> FromResponseBody(contentType)
      Nothing::class.java -> ToNothing(contentType)
      else -> null
    } as Marshaller<Any>?
  }
}
