package misk

import misk.web.typeLiteral
import kotlin.reflect.KFunction

fun KFunction<*>.asAction(): Action {
  val parameterTypes = this.parameters.drop(1).map {
    it.type.typeLiteral
  }
  val returnType = this.returnType.typeLiteral

  return Action(this, parameterTypes, returnType)
}
