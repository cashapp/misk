package misk.web.requestdeadlines

import com.google.inject.TypeLiteral
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.client.ClientApplicationInterceptorFactory
import misk.client.DeadlinePropagationInterceptor
import misk.inject.KAbstractModule
import misk.scope.ActionScoped
import misk.scope.ActionScopedProvider
import misk.scope.ActionScopedProviderModule
import misk.web.HttpCall
import misk.web.NetworkInterceptor
import misk.web.interceptors.RequestDeadlineInterceptor
import java.time.Clock
import java.time.Instant

class DeadlinePropagationModule() : KAbstractModule() {
  override fun configure() {
    install(
      object : ActionScopedProviderModule() {
        override fun configureProviders() {
          bindProvider(TypeLiteral.get(RequestDeadline::class.java), RequestDeadlineProvider::class)
        }
      }
    )
    // Server-side
    multibind<NetworkInterceptor.Factory>().to<RequestDeadlineInterceptor.Factory>()
    // Client-side
    multibind<ClientApplicationInterceptorFactory>().to<DeadlinePropagationInterceptor.Factory>()
  }

  @Singleton
  class RequestDeadlineProvider
  @Inject
  constructor(private val clock: Clock, private val httpCall: ActionScoped<HttpCall>) :
    ActionScopedProvider<RequestDeadline> {

    override fun get(): RequestDeadline {
      val absoluteDeadline = httpCall.get().requestHeaders[RequestDeadlineInterceptor.MISK_REQUEST_DEADLINE_HEADER]?.let { Instant.parse(it) }

      val deadline =
        when {
          absoluteDeadline != null -> absoluteDeadline
          else -> null
        }

      return RequestDeadline(clock, deadline)
    }
  }
}
