package misk.web

import misk.web.actions.WebAction
import kotlin.reflect.KFunction

interface NetworkChain {
  val action: WebAction
  val request: Request
  val function: KFunction<*>
  fun proceed(request: Request): Response<*>
}
