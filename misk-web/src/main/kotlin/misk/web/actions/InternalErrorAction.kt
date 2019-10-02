package misk.web.actions

import misk.security.authz.Unauthenticated
import misk.web.Get
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InternalErrorAction @Inject constructor() : WebAction {
  @Get("/error")
  @Unauthenticated
  fun error(): Nothing {
    throw UnsupportedOperationException()
  }
}
