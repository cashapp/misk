package misk

import kotlin.reflect.KFunction
import misk.web.HttpCall
import misk.web.actions.WebAction

interface Chain {
  val action: WebAction
  val args: List<Any?>
  val function: KFunction<*>
  val httpCall: HttpCall

  fun proceed(args: List<Any?>): Any
}
