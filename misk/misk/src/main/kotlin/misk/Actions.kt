package misk

import misk.web.typeLiteral
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.instanceParameter

fun KFunction<*>.asAction(): Action {
  val instanceParameter = this.instanceParameter
      ?: throw IllegalArgumentException("only methods may be actions")

  val parameterTypes = this.parameters.drop(1).map { it.type.typeLiteral }
  val returnType = this.returnType.typeLiteral
  val name = instanceParameter.type.classifier?.let {
    when (it) {
      is KClass<*> -> it.simpleName
      else -> this.name
    }
  } ?: this.name

  return Action(name, this, parameterTypes, returnType)
}
