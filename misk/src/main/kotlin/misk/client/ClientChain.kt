package misk.client

import retrofit2.Call
import retrofit2.Callback

interface ClientChain {
  val action: ClientAction
  val args: List<*>
  val call: Call<Any>
  val callback: Callback<Any>
  fun proceed(args: List<*>, callback: Callback<Any>)
}
