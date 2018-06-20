package misk.client

import misk.inject.KAbstractModule

/** Installs an application level [ClientApplicationInterceptor] which can observe calls to a peer */
class ClientApplicationInterceptorModule<T : ClientApplicationInterceptor.Factory>(
  private val interceptorFactory: Class<T>
) : KAbstractModule() {
  override fun configure() {
    multibind<ClientApplicationInterceptor.Factory>().to(interceptorFactory)
  }

  companion object {
    @JvmStatic
    fun <T : ClientApplicationInterceptor.Factory> create(interceptorFactory: Class<T>) =
        ClientApplicationInterceptorModule(interceptorFactory)

    inline fun <reified T : ClientApplicationInterceptor.Factory> create() = create(T::class.java)
  }

}