package com.squareup.urlshortener

import misk.exceptions.NotFoundException
import misk.web.Get
import misk.web.PathParam
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import okio.BufferedSink
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortUrlWebAction : WebAction {
  @Inject lateinit var urlStore: UrlStore

  @Get("/{token:[^/_]+}")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun follow(@PathParam token: String): Response<ResponseBody> {
    val longUrl = urlStore.tokenToUrl(UrlToken(token)) ?: throw NotFoundException(token)

    val emptyBody = object : ResponseBody {
      override fun writeTo(sink: BufferedSink) {
      }
    }

    return Response<ResponseBody>(
        headers = Headers.of("Location", longUrl.toString()),
        body = emptyBody,
        statusCode = 302
    )
  }
}
