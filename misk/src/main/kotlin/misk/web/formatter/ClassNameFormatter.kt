package misk.web.formatter

import jakarta.inject.Inject
import kotlin.reflect.KClass

class ClassNameFormatter @Inject constructor() {
  companion object {
    fun <T : Any> format(kclass: KClass<T>): String {
      return when (kclass.qualifiedName) {
        null -> kclass.toString().split("class ").last()
        else -> kclass.qualifiedName.toString()
      }
    }
  }
}
