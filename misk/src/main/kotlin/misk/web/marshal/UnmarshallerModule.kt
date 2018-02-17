package misk.web.marshal

import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import kotlin.reflect.KClass

class UnmarshallerModule<T : Unmarshaller.Factory>(val kclass: KClass<T>) : KAbstractModule() {
  override fun configure() {
    binder().addMultibinderBinding<Unmarshaller.Factory>()
        .to(kclass.java)
  }

  companion object {
    inline fun <reified T : Unmarshaller.Factory> create() = UnmarshallerModule(T::class)
  }
}
