package misk.client

import retrofit2.Callback
import retrofit2.Call

internal class RealClientChain(
  override val action: ClientAction,
  override val args: List<*>,
  override val call: Call<Any>,
  override val callback: Callback<Any>,
  private val interceptors: List<ClientApplicationInterceptor>,
  private val index: Int = 0
) : ClientChain {
  override fun proceed(args: List<*>, callback: Callback<Any>) {
    check(index < interceptors.size) { "final interceptor must be terminal" }
    val next = RealClientChain(action, args, call, callback, interceptors, index + 1)
    interceptors[index].intercept(next)
  }
}
