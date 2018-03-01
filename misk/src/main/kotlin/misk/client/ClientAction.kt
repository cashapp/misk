package misk.client

import kotlin.reflect.KFunction
import kotlin.reflect.KType

data class ClientAction(
  val name: String,
  val function: KFunction<*>,
  val parameterTypes: List<KType>,
  val returnType: KType
) {
  internal constructor(clientName: String, method: KFunction<*>) :
      this("$clientName.${method.name}",
          method,
          method.parameters.drop(1).map { it.type }, // drop the 'this' parameter
          method.returnType)
}