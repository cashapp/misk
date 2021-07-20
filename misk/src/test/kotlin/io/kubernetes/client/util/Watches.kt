package io.kubernetes.client.util

object Watches {
  fun <T> newResponse(type: String?, value: T): Watch.Response<T> {
    return Watch.Response(type, value)
  }
}
