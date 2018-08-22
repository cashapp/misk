package misk.web.resources

import misk.Action
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.Response
import javax.inject.Inject
import javax.inject.Singleton

class StaticResourceInterceptor(val factory: Factory) : NetworkInterceptor {
  override fun intercept(chain: NetworkChain): Response<*> {


    return factory.staticResourceMapper.getResponse(chain.request.url) ?: return chain.proceed(chain.request)
  }

  @Singleton
  class Factory : NetworkInterceptor.Factory {
    @Inject lateinit var staticResourceMapper: StaticResourceMapper

    override fun create(action: Action): NetworkInterceptor? {
      return StaticResourceInterceptor(this)
    }
  }
}
