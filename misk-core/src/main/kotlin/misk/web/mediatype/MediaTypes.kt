package misk.web.mediatype

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType

/**
 * Note order alphabetically, but keep pairs together.
 */
object MediaTypes {

  const val ALL = "*/*"
  val ALL_MEDIA_TYPE = ALL.asMediaType()

  const val APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded;charset=utf-8"
  val APPLICATION_FORM_URLENCODED_MEDIA_TYPE = APPLICATION_FORM_URLENCODED.asMediaType()

  const val APPLICATION_GRPC = "application/grpc"
  val APPLICATION_GRPC_MEDIA_TYPE = APPLICATION_GRPC.asMediaType()

  const val APPLICATION_GRPC_PROTOBUF = "application/grpc+proto"
  val APPLICATION_GRPC_PROTOBUF_MEDIA_TYPE = APPLICATION_GRPC_PROTOBUF.asMediaType()

  const val APPLICATION_JAVASCRIPT = "application/javascript"
  val APPLICATION_JAVASCRIPT_MEDIA_TYPE = APPLICATION_JAVASCRIPT.asMediaType()

  const val APPLICATION_JSON = "application/json;charset=utf-8"
  val APPLICATION_JSON_MEDIA_TYPE = APPLICATION_JSON.asMediaType()

  const val APPLICATION_JWT = "application/jwt"
  val APPLICATION_JWT_MEDIA_TYPE = APPLICATION_JWT.asMediaType()

  const val APPLICATION_OCTETSTREAM = "application/octetstream"
  val APPLICATION_OCTETSTREAM_MEDIA_TYPE = APPLICATION_OCTETSTREAM.asMediaType()

  const val APPLICATION_PROTOBUF = "application/x-protobuf"
  val APPLICATION_PROTOBUF_MEDIA_TYPE = APPLICATION_PROTOBUF.asMediaType()

  const val APPLICATION_XML = "application/xml"
  val APPLICATION_XML_MEDIA_TYPE = APPLICATION_XML.asMediaType()

  const val APPLICATION_ZIP = "application/zip"
  val APPLICATION_ZIP_MEDIA_TYPE = APPLICATION_ZIP.asMediaType()

  const val FORM_DATA = "multipart/form-data"
  val FORM_DATA_MEDIA_TYPE = FORM_DATA.asMediaType()

  const val IMAGE_GIF = "image/gif"
  val IMAGE_GIF_MEDIA_TYPE = IMAGE_GIF.asMediaType()

  const val IMAGE_ICO = "image/x-icon"
  val IMAGE_ICO_MEDIA_TYPE = IMAGE_ICO.asMediaType()

  const val IMAGE_JPEG = "image/jpeg"
  val IMAGE_JPEG_MEDIA_TYPE = IMAGE_JPEG.asMediaType()

  const val IMAGE_PNG = "image/png"
  val IMAGE_PNG_MEDIA_TYPE = IMAGE_PNG.asMediaType()

  const val IMAGE_SVG = "image/svg+xml"
  val IMAGE_SVG_MEDIA_TYPE = IMAGE_SVG.asMediaType()

  const val IMAGE_TIFF = "image/tiff"
  val IMAGE_TIFF_MEDIA_TYPE = IMAGE_TIFF.asMediaType()

  const val TEXT_CSS = "text/css"
  val TEXT_CSS_MEDIA_TYPE = TEXT_CSS.asMediaType()

  const val TEXT_HTML = "text/html"
  val TEXT_HTML_MEDIA_TYPE = TEXT_HTML.asMediaType()

  const val TEXT_PLAIN_UTF8 = "text/plain;charset=utf-8"
  val TEXT_PLAIN_UTF8_MEDIA_TYPE = TEXT_PLAIN_UTF8.asMediaType()

  const val TURBO_STREAM = "text/vnd.turbo-stream.html"
  val TURBO_STREAM_MEDIA_TYPE = TURBO_STREAM.asMediaType()

  fun fromFileExtension(ext: String): MediaType {
    return when (ext) {
      "css" -> TEXT_CSS_MEDIA_TYPE
      "form-data" -> FORM_DATA_MEDIA_TYPE
      "gif" -> IMAGE_GIF_MEDIA_TYPE
      "html", "htm" -> TEXT_HTML_MEDIA_TYPE
      "ico" -> IMAGE_ICO_MEDIA_TYPE
      "jpeg", "jpg" -> IMAGE_JPEG_MEDIA_TYPE
      "js" -> APPLICATION_JAVASCRIPT_MEDIA_TYPE
      "json" -> APPLICATION_JSON_MEDIA_TYPE
      "jwt" -> APPLICATION_JWT_MEDIA_TYPE
      "png" -> IMAGE_PNG_MEDIA_TYPE
      "svg" -> IMAGE_SVG_MEDIA_TYPE
      "tiff" -> IMAGE_TIFF_MEDIA_TYPE
      "txt" -> TEXT_PLAIN_UTF8_MEDIA_TYPE
      "xml" -> APPLICATION_XML_MEDIA_TYPE
      "zip" -> APPLICATION_ZIP_MEDIA_TYPE
      else -> APPLICATION_OCTETSTREAM_MEDIA_TYPE
    }
  }
}

fun String.asMediaType() = this.toMediaType()
fun String.asMediaRange() = MediaRange.parse(this)

internal val MediaType.wildcardCount
  get() = when {
    type == "*" -> 2
    subtype == "*" -> 1
    else -> 0
  }

fun MediaType.compareTo(other: MediaType) = wildcardCount - other.wildcardCount
