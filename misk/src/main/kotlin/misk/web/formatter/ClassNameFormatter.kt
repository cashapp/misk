package misk.web.formatter

import kotlin.reflect.KClass

class ClassNameFormatter {
  fun <T : Any> format(kclass: KClass<T>): String {
    return when (kclass.qualifiedName) {
      null -> kclass.toString().split("class ").last()
      else -> kclass.qualifiedName.toString()
    }
  }
}
