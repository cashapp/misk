package misk.client

import misk.client.ClientAction
import okhttp3.Interceptor

interface ClientApplicationInterceptorFactory {
  fun create(action: ClientAction): Interceptor?
}
