package misk.client

import retrofit2.Call

internal class RealBeginClientCallChain(
  override val action: ClientAction,
  override val args: List<*>,
  private val interceptors: List<ClientApplicationInterceptor>,
  private val index: Int = 0
) : BeginClientCallChain {
  override fun proceed(args: List<*>): Call<Any> {
    check(index < interceptors.size) { "final interceptor must be terminal" }
    val next = RealBeginClientCallChain(action, args, interceptors, index + 1)
    return interceptors[index].interceptBeginCall(next)
  }
}
