package misk.web.mediatype

import okhttp3.MediaType

object MediaTypes {
  const val APPLICATION_JSON = "application/json;charset=utf-8"
  val APPLICATION_JSON_MEDIA_TYPE = APPLICATION_JSON.asMediaType()

  const val TEXT_PLAIN_UTF8 = "text/plain;charset=utf-8"
  val TEXT_PLAIN_UTF8_MEDIA_TYPE = TEXT_PLAIN_UTF8.asMediaType()

  const val APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded;charset=utf-8"

  const val ALL = "*/*"
  val ALL_MEDIA_TYPE = ALL.asMediaType()

  const val APPLICATION_JAVASCRIPT = "application/javascript"
  val APPLICATION_JAVASCRIPT_MEDIA_TYPE = APPLICATION_JAVASCRIPT.asMediaType()

  const val APPLICATION_OCTETSTREAM = "application/octetstream"
  val APPLICATION_OCTETSTREAM_MEDIA_TYPE = APPLICATION_OCTETSTREAM.asMediaType()

  const val APPLICATION_PROTOBUF = "application/x-protobuf"
  val APPLICATION_PROTOBUF_MEDIA_TYPE = APPLICATION_PROTOBUF.asMediaType()

  const val TEXT_CSS = "text/css"
  val TEXT_CSS_MEDIA_TYPE = TEXT_CSS.asMediaType()

  const val TEXT_HTML = "text/html"
  val TEXT_HTML_MEDIA_TYPE = TEXT_HTML.asMediaType()

  const val IMAGE_PNG = "image/png"
  val IMAGE_PNG_MEDIA_TYPE = IMAGE_PNG.asMediaType()

  const val APPLICATION_GRPC = "application/grpc"
  val APPLICATION_GRPC_MEDIA_TYPE = APPLICATION_GRPC.asMediaType()

  fun fromFileExtension(ext: String) : MediaType {
    return when (ext) {
      "css" -> TEXT_CSS_MEDIA_TYPE
      "html", "htm" -> TEXT_HTML_MEDIA_TYPE
      "js" -> APPLICATION_JAVASCRIPT_MEDIA_TYPE
      "png" -> IMAGE_PNG_MEDIA_TYPE
      else -> APPLICATION_OCTETSTREAM_MEDIA_TYPE
    }
  }
}

fun String.asMediaType() = MediaType.get(this)!!
fun String.asMediaRange() = MediaRange.parse(this)

internal val MediaType.wildcardCount
  get() = when {
    type() == "*" -> 2
    subtype() == "*" -> 1
    else -> 0
  }

fun MediaType.compareTo(other: MediaType) = wildcardCount - other.wildcardCount