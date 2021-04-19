package com.squareup.exemplar.actions

import com.squareup.exemplar.protos.HelloWebRequest
import com.squareup.exemplar.protos.HelloWebResponse
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
  @Description("""
    A web action that expects a HelloWebRequest and returns a HelloWebResponse
  """)
  fun hello(
    @RequestBody request: HelloWebRequest
  ): Response<HelloWebResponse> {
    return Response(HelloWebResponse())
  }
}
