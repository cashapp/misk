package misk.client

import retrofit2.Call

interface BeginClientCallChain {
  val action: ClientAction
  val args: List<*>
  fun proceed(args: List<*>): Call<Any>
}
