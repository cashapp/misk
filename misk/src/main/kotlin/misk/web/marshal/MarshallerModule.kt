package misk.web.marshal

import misk.inject.KAbstractModule
import kotlin.reflect.KClass

class MarshallerModule<T : Marshaller.Factory>(val kclass: KClass<T>) : KAbstractModule() {
  override fun configure() {
    multibind<Marshaller.Factory>().to(kclass.java)
  }

  companion object {
    inline fun <reified T : Marshaller.Factory> create() = MarshallerModule(T::class)
  }
}
