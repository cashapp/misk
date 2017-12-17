package com.squareup.exemplar

import misk.web.Get
import misk.web.PathParam
import misk.web.RequestHeaders
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import javax.inject.Singleton

@Singleton
class HelloWebAction : WebAction {
  @Get("/hello/{name}")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun hello(@PathParam name: String, @RequestHeaders headers: Headers): HelloResponse {
    return HelloResponse("YO", name.toUpperCase())
  }
}

data class HelloResponse(val greeting: String, val name: String)
