package misk

import kotlin.reflect.KFunction
import kotlin.reflect.KType

data class Action(
    val name: String,
    val function: KFunction<*>,
    val parameterTypes: List<KType>,
    val returnType: KType
)
