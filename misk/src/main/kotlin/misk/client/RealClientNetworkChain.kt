package misk.client

import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

internal class RealClientNetworkChain(
  private val okhttpChain: okhttp3.Interceptor.Chain,
  override val action: ClientAction
) : ClientNetworkChain {
  override val request: Request = okhttpChain.request()
  override fun proceed(request: Request): Response = okhttpChain.proceed(request)

  override fun withConnectTimeout(timeout: Int, unit: TimeUnit): ClientNetworkChain {
    return RealClientNetworkChain(okhttpChain.withConnectTimeout(timeout, unit), action)
  }

  override fun withReadTimeout(timeout: Int, unit: TimeUnit): ClientNetworkChain {
    return RealClientNetworkChain(okhttpChain.withReadTimeout(timeout, unit), action)
  }

  override fun withWriteTimeout(timeout: Int, unit: TimeUnit): ClientNetworkChain {
    return RealClientNetworkChain(okhttpChain.withWriteTimeout(timeout, unit), action)
  }
}
