package misk.client

import kotlin.reflect.KFunction
import kotlin.reflect.KType

/** Metadata about a client action */
data class ClientAction(
  /** The name of the action, composed of the name of the client + the name of the method */
  val name: String,

  /** The function that was used to invoke the action */
  val function: KFunction<*>,

  /** The parameter types to the action */
  val parameterTypes: List<KType>,

  /** The return type fo the action */
  val returnType: KType
) {
  internal constructor(clientName: String, method: KFunction<*>) :
    this(
      "$clientName.${method.name}",
      method,
      method.parameters.drop(1).map { it.type }, // drop the 'this' parameter
      method.returnType
    )
}
