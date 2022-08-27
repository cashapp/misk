package com.squareup.library.actions

import misk.security.authz.Unauthenticated
import misk.web.PathParam
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HelloWebPostAction @Inject constructor() : WebAction {
  @Post("/hello/{name}")
  @Unauthenticated
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun hello(@PathParam name: String, @RequestBody body: PostBody): HelloPostResponse {
    return HelloPostResponse(body.greeting, name.toUpperCase())
  }
}

data class HelloPostResponse(val greeting: String, val name: String)

data class PostBody(val greeting: String)
