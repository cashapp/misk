package misk.client

import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding

/** Installs a [ClientNetworkInterceptor] to observe outgoing HTTP traffic */
class ClientNetworkInterceptorModule<T : ClientNetworkInterceptor.Factory> private constructor(
  private val interceptorFactory: Class<T>
) : KAbstractModule() {
  override fun configure() {
    binder().addMultibinderBinding<ClientNetworkInterceptor.Factory>().to(interceptorFactory)
  }

  companion object {
    fun <T : ClientNetworkInterceptor.Factory> create(clazz: Class<T>) =
        ClientNetworkInterceptorModule(clazz)

    inline fun <reified T : ClientNetworkInterceptor.Factory> create() = create(T::class.java)
  }
}