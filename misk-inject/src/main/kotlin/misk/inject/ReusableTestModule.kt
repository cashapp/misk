package misk.inject

import misk.annotation.ExperimentalMiskApi
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * This class should be extended by test modules used in tests,
 * for Misk to reuse the Guice injector across tests for significantly faster test suite performance.
 */
@ExperimentalMiskApi
abstract class ReusableTestModule: KAbstractModule() {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false

    val thisProperties = this::class.memberProperties
    val otherProperties = other::class.memberProperties

    if (thisProperties.size != otherProperties.size) return false

    for (property in thisProperties) {
      property.isAccessible = true
      val thisValue = (property as KProperty1<ReusableTestModule, *>).get(this)
      val otherValue = property.get(other as ReusableTestModule)
      if (thisValue != otherValue) return false
    }

    return true
  }

  override fun hashCode(): Int {
    var result = javaClass.hashCode()
    for (property in this::class.memberProperties) {
      property.isAccessible = true
      result = 31 * result + ((property as KProperty1<ReusableTestModule, *>).get(this)?.hashCode() ?: 0)
    }
    return result
  }
}
