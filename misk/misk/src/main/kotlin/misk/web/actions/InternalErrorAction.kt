package misk.web.actions

import misk.web.Get
import javax.inject.Singleton

@Singleton
class InternalErrorAction : WebAction {
  @Get("/error")
  fun error(): Nothing {
    throw UnsupportedOperationException()
  }
}
