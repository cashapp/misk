package misk

import misk.web.actions.WebAction
import kotlin.reflect.KFunction

interface Chain {
  val action: WebAction
  val args: List<Any?>
  val function: KFunction<*>
  fun proceed(args: List<Any?>): Any?
}
