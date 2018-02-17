package misk.web.exceptions

import com.google.inject.TypeLiteral
import com.google.inject.multibindings.Multibinder
import misk.inject.KAbstractModule
import kotlin.reflect.KClass

class ExceptionMapperModule<T : ExceptionMapper<*>>(
    private val kclass: KClass<T>
) : KAbstractModule() {
  override fun configure() {
    Multibinder.newSetBinder(binder(), exceptionMapperTypeLiteral)
        .addBinding()
        .to(kclass.java)
  }

  companion object {
    inline fun <reified T : ExceptionMapper<*>> create() = ExceptionMapperModule(T::class)

    private val exceptionMapperTypeLiteral = object : TypeLiteral<ExceptionMapper<*>>() {}
  }
}
