package misk.web

import misk.web.actions.WebAction
import okio.BufferedSink
import kotlin.reflect.KFunction

interface NetworkChain {
  val action: WebAction
  val request: Request
  val responseBodySink: BufferedSink?
  val function: KFunction<*>
  fun proceed(request: Request): Response<*>
}
