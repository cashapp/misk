package misk.web.actions

import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.ConcurrencyLimitsOptOut
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InternalErrorAction @Inject constructor() : WebAction {
  @Get("/error")
  @ConcurrencyLimitsOptOut // TODO: Remove after 2020-08-01 (or use @AvailableWhenDegraded).
  @Unauthenticated
  fun error(): Nothing {
    throw UnsupportedOperationException()
  }
}
