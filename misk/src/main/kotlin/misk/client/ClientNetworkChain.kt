package misk.client

import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

interface ClientNetworkChain {
  val action: ClientAction
  val request: Request
  fun proceed(request: Request): Response

  /**
   * Override the connect timeout.
   */
  fun withConnectTimeout(timeout: Int, unit: TimeUnit): ClientNetworkChain

  /**
   * Override the read timeout.
   */
  fun withReadTimeout(timeout: Int, unit: TimeUnit): ClientNetworkChain

  /**
   * Override the write timeout.
   */
  fun withWriteTimeout(timeout: Int, unit: TimeUnit): ClientNetworkChain
}
