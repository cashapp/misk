package com.squareup.urlshortener

import misk.exceptions.BadRequestException
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateShortUrlWebAction : WebAction {
  @Inject lateinit var endpointConfig: EndpointConfig
  @Inject lateinit var urlStore: UrlStore

  @Post("/create")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun createShortUrl(@RequestBody body: Request): Response {
    val baseUrl = HttpUrl.parse(endpointConfig.base_url)!!
    val longUrl = HttpUrl.parse(body.long_url) ?: throw BadRequestException()

    val token = urlStore.urlToToken(longUrl)

    val shortUrl = baseUrl.newBuilder()
        .addPathSegment(token)
        .build()

    return Response(shortUrl.toString())
  }

  data class Request(val long_url: String)

  data class Response(val short_url: String)
}
