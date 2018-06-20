package misk.web.marshal

import misk.inject.KAbstractModule
import kotlin.reflect.KClass

class UnmarshallerModule<T : Unmarshaller.Factory>(val kclass: KClass<T>) : KAbstractModule() {
  override fun configure() {
    multibind<Unmarshaller.Factory>().to(kclass.java)
  }

  companion object {
    inline fun <reified T : Unmarshaller.Factory> create() = UnmarshallerModule(T::class)
  }
}
