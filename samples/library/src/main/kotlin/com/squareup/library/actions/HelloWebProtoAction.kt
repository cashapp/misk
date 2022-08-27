package com.squareup.library.actions

import com.squareup.library.protos.HelloWebRequest
import com.squareup.library.protos.HelloWebResponse
import misk.security.authz.Unauthenticated
import misk.web.Description
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import javax.inject.Inject

class HelloWebProtoAction @Inject constructor() : WebAction {
  @Post("/hello/proto/")
  @Unauthenticated
  @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
  @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
  @Description(
    """
    Given a request containing a name, nickname, and list of greetings
    return a suitable response.
  """
  )
  fun hello(
    @RequestBody request: HelloWebRequest
  ): Response<HelloWebResponse> {
    return Response(
      HelloWebResponse().newBuilder()
        .greeting(greeting(request))
        .name(request.nick_name?.toUpperCase() ?: request.name.toUpperCase())
        .build()
    )
  }

  private fun greeting(request: HelloWebRequest) : String {
    return if (request.greetings.isNotEmpty()) {
      request.greetings.joinToString(separator = " ")
    } else {
      "Yo"
    }
  }
}
