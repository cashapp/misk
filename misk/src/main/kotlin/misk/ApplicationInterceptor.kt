package misk

interface ApplicationInterceptor {
  fun intercept(chain: Chain): Any

  interface Factory {
    fun create(action: Action): ApplicationInterceptor?
  }
}
