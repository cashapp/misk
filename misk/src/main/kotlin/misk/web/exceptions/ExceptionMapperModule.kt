package misk.web.exceptions

import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import misk.inject.KAbstractModule
import kotlin.reflect.KClass

/**
 * Binds a [Throwable] to an [ExceptionMapper].
 *
 * When an Exception occurs dispatching an Action, the bound ExceptionMapper is called to handle
 * the Exception. If there is not an explicit binding for the thrown Exception, the mapper for the
 * closest superclass is used.
 *
 * Given the example code
 *
 * ```
 * install(ExceptionMapperModule.create<ActionException, ActionExceptionMapper>())
 * install(ExceptionMapperModule.create<MyActionException, MyActionExceptionMapper>())
 *
 * class MyActionException : ActionException {}
 * class MyOtherActionException : ActionException {}
 * ```
 *
 * MyActionException maps to the specific MyActionExceptionMapper and MyOtherActionException
 * maps to the ActionExceptionMapper since uses the binding of the closest bound superclass.
 */
class ExceptionMapperModule<M : ExceptionMapper<T>, in T : Throwable>(
  private val exceptionClass: KClass<T>,
  private val mapperClass: KClass<M>
) : KAbstractModule() {
  override fun configure() {
    MapBinder.newMapBinder(binder(), exceptionTypeLiteral, exceptionMapperTypeLiteral)
      .addBinding(exceptionClass)
      .to(mapperClass.java)
  }

  companion object {
    inline fun <reified T : Throwable, reified M : ExceptionMapper<T>> create() =
      ExceptionMapperModule(
        T::class, M::class
      )

    private val exceptionMapperTypeLiteral = object : TypeLiteral<ExceptionMapper<*>>() {}
    private val exceptionTypeLiteral = object : TypeLiteral<KClass<*>>() {}
  }
}
