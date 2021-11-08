package misk.client

import okhttp3.Interceptor

interface ClientApplicationInterceptorFactory {
  fun create(action: ClientAction): Interceptor?
}
