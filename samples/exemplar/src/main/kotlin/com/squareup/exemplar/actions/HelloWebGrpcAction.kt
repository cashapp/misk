package com.squareup.exemplar.actions

import com.squareup.exemplar.protos.HelloWebRequest
import com.squareup.exemplar.protos.HelloWebResponse
import com.squareup.exemplar.protos.HelloWebServiceHelloBlockingServer
import jakarta.inject.Inject
import misk.security.authz.Unauthenticated
import misk.web.actions.WebAction

class HelloWebGrpcAction @Inject constructor() : HelloWebServiceHelloBlockingServer, WebAction {
  @Unauthenticated
  override fun Hello(request: HelloWebRequest): HelloWebResponse =
    HelloWebResponse(greeting = greeting(request), name = (request.nick_name?.uppercase() ?: request.name.uppercase()))

  private fun greeting(request: HelloWebRequest): String {
    return if (request.greetings.isNotEmpty()) {
      request.greetings.joinToString(separator = " ")
    } else {
      "Yo"
    }
  }
}
