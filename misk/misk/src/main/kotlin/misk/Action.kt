package misk

import com.google.inject.TypeLiteral
import kotlin.reflect.KFunction

data class Action(
  val name: String,
  val function: KFunction<*>,
  val parameterTypes: List<TypeLiteral<*>>,
  val returnType: TypeLiteral<*>
)
