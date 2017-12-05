package misk

interface Interceptor {
  fun intercept(chain: Chain): Any?

  interface Factory {
    fun create(action: Action): Interceptor?
  }
}
