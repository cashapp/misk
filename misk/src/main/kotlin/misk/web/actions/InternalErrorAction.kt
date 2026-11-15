package misk.web.actions

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.security.authz.Unauthenticated
import misk.web.Get

@Singleton
class InternalErrorAction @Inject constructor() : WebAction {
  @Get("/error")
  @Unauthenticated
  fun error(): Nothing {
    throw UnsupportedOperationException()
  }
}
