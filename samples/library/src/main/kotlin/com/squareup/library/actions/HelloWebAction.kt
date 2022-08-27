package com.squareup.library.actions

import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.QueryParam
import misk.web.RequestHeaders
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HelloWebAction @Inject constructor() : WebAction {
  @Get("/hello/{name}")
  @Unauthenticated
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun hello(
    @PathParam name: String,
    @Suppress("UNUSED_PARAMETER")
    @RequestHeaders headers: Headers,
    @QueryParam nickName: String?,
    @QueryParam greetings: List<String>?
  ): HelloResponse {
    return HelloResponse(
        greetings?.joinToString(separator = " ") ?: "YO",
        nickName?.toUpperCase() ?: name.toUpperCase())
  }
}

data class HelloResponse(val greeting: String, val name: String)
