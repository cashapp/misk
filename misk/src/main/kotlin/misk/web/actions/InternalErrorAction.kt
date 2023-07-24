package misk.web.actions

import misk.security.authz.Unauthenticated
import misk.web.Get
import com.google.inject.Inject
import com.google.inject.Singleton

@Singleton
class InternalErrorAction @Inject constructor() : WebAction {
  @Get("/error")
  @Unauthenticated
  fun error(): Nothing {
    throw UnsupportedOperationException()
  }
}
