package misk.web.interceptors

import misk.Action
import misk.Chain
import misk.Interceptor
import misk.inject.typeLiteral
import misk.web.Response
import javax.inject.Singleton

/** Wraps the method's result `T` as a `Response<T>` if necessary. */
@Singleton
class BoxResponseInterceptorFactory : Interceptor.Factory {
  val interceptor = object : Interceptor {
    override fun intercept(chain: Chain): Any? {
      val result = chain.proceed(chain.args)
      // NB(young): Something down the chain could have returned a Response, so avoid double
      // wrapping it.
      return if (result is Response<*>) result else Response(result)
      return Response(result)
    }
  }

  override fun create(action: Action): Interceptor? {
    return when {
      action.returnType.typeLiteral().rawType == Response::class.java -> null
      else -> interceptor
    }
  }
}

