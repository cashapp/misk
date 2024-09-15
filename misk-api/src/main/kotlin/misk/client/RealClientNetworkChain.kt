package misk.client

import okhttp3.Request
import okhttp3.Response

internal class RealClientNetworkChain(
  private val okhttpChain: okhttp3.Interceptor.Chain,
  override val action: ClientAction
) : ClientNetworkChain {
  override val request: okhttp3.Request = okhttpChain.request()
  override fun proceed(request: Request): Response = okhttpChain.proceed(request)
}
