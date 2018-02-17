package misk.testing

import com.google.inject.Module
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import org.junit.jupiter.api.extension.AfterEachCallback
import kotlin.reflect.KClass

/** Module registering a callback that fires after each test */
class AfterEachExtensionModule<T : AfterEachCallback> constructor(
    private val kclass: KClass<T>
) : KAbstractModule() {

  override fun configure() {
    binder().addMultibinderBinding<AfterEachCallback>()
        .to(kclass.java)
  }

  companion object {
    inline fun <reified T : AfterEachCallback> create(): Module =
        AfterEachExtensionModule(T::class)
  }
}
