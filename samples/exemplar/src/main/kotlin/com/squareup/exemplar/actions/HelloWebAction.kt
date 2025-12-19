package com.squareup.exemplar.actions

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.security.authz.Unauthenticated
import misk.tokens.TokenGenerator
import misk.web.Get
import misk.web.PathParam
import misk.web.QueryParam
import misk.web.RequestHeaders
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import okhttp3.Headers

@Singleton
class HelloWebAction @Inject constructor(private val tokenGenerator: TokenGenerator) : WebAction {
  @Get("/hello/{name}")
  @Unauthenticated
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun hello(
    @PathParam name: String,
    @Suppress("UNUSED_PARAMETER") @RequestHeaders headers: Headers,
    @QueryParam nickName: String?,
    @QueryParam greetings: List<String>?,
  ): HelloResponse {
    return HelloResponse(
      greetings?.joinToString(separator = " ") ?: tokenGenerator.generate(),
      nickName?.uppercase() ?: name.uppercase(),
    )
  }
}

data class HelloResponse(val greeting: String, val name: String)
