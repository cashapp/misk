package misk.web.interceptors

import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.Response
import misk.web.actions.WebAction
import javax.inject.Inject

internal class TestAction @Inject constructor() : WebAction {
  @Get("/call/{desiredStatusCode}")
  @Unauthenticated
  fun call(@PathParam desiredStatusCode: Int): Response<String> {
    return Response("foo", statusCode = desiredStatusCode)
  }
}
