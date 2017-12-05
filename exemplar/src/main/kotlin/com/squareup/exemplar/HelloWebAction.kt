package com.squareup.exemplar

import misk.web.Get
import misk.web.JsonResponseBody
import misk.web.RequestHeaders
import misk.web.actions.WebAction
import okhttp3.Headers
import javax.inject.Singleton

@Singleton
class HelloWebAction : WebAction {
  @Get("/hello/{name}")
  @JsonResponseBody
  fun hello(
    name: String,
    @RequestHeaders headers: Headers
  ): HelloResponse {
    return HelloResponse("YO", name.toUpperCase())
  }
}

data class HelloResponse(val greeting: String, val name: String)
