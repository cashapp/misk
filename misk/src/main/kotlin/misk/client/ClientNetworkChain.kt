package misk.client

import okhttp3.Request
import okhttp3.Response

interface ClientNetworkChain {
  val action: ClientAction
  val request: okhttp3.Request
  fun proceed(request: Request): Response
}
