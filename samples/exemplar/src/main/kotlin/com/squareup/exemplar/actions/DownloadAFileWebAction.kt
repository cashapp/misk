package com.squareup.exemplar.actions

import com.google.common.net.HttpHeaders
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import okhttp3.Headers.Companion.headersOf

@Singleton
class DownloadAFileWebAction @Inject constructor() : WebAction {
  @Get("/download/{name}")
  @Unauthenticated
  @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
  fun download(@PathParam name: String): Response<String> {
    return Response(
      body = "Hey $name, I made you this file",
      headers = headersOf(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$name.txt\""),
    )
  }
}
