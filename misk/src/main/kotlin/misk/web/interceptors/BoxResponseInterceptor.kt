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

