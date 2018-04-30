package misk.web

import misk.Action
import javax.inject.Inject
import javax.inject.Singleton

class StaticResourceInterceptor(val factory: Factory) : NetworkInterceptor {
  override fun intercept(chain: NetworkChain): Response<*> {
    val urlPath = chain.request.url.encodedPath()
    return factory.staticResourceMapper.getResponse(urlPath) ?: return chain.proceed(chain.request)
  }

  @Singleton
  class Factory : NetworkInterceptor.Factory {
    @Inject lateinit var staticResourceMapper: StaticResourceMapper

    override fun create(action: Action): NetworkInterceptor? {
      return StaticResourceInterceptor(this)
    }
  }
}
