package misk.web.mediatype

import okhttp3.MediaType

object MediaTypes {
  const val APPLICATION_JSON = "application/json;charset=utf-8"
  val APPLICATION_JSON_MEDIA_TYPE = APPLICATION_JSON.asMediaType()

  const val TEXT_PLAIN_UTF8 = "text/plain;charset=utf-8"
  val TEXT_PLAIN_UTF8_MEDIA_TYPE = TEXT_PLAIN_UTF8.asMediaType()

  const val ALL = "*/*"
  val ALL_MEDIA_TYPE = ALL.asMediaType()
}

fun String.asMediaType() = MediaType.parse(this)!!
fun String.asMediaRange() = MediaRange.parse(this)
