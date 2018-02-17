package misk.testing

import com.google.inject.Module
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import org.junit.jupiter.api.extension.BeforeEachCallback
import kotlin.reflect.KClass

/** Module registering a callback that fires before each test */
class BeforeEachExtensionModule<T : BeforeEachCallback> constructor(
    private val kclass: KClass<T>
) : KAbstractModule() {

  override fun configure() {
    binder().addMultibinderBinding<BeforeEachCallback>()
        .to(kclass.java)
  }

  companion object {
    inline fun <reified T : BeforeEachCallback> create(): Module =
        BeforeEachExtensionModule(T::class)
  }
}
