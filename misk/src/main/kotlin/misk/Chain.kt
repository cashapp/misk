package misk

import misk.web.actions.WebAction
import kotlin.reflect.KFunction
import misk.web.HttpCall

interface Chain {
  val action: WebAction
  val args: List<Any?>
  val httpCall: HttpCall
  val function: KFunction<*>
  fun proceed(args: List<Any?>): Any
}
