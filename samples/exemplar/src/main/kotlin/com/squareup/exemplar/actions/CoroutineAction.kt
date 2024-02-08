package com.squareup.exemplar.actions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.RequestHeaders
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import javax.inject.Singleton

@Singleton
class CoroutineAction : WebAction {

  @Get(pathPattern = "/coroutine/run/{id}")
  @Unauthenticated
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun run(
    @PathParam("id") id: String,
    @Suppress("UNUSED_PARAMETER")
    @RequestHeaders header: Headers
  ): Response {
    return runBlocking(Dispatchers.IO) {
      Thread.sleep(10)
      Response(id, "message")
    }
  }

  data class Response(private val id: String, private val msg: String)
}
