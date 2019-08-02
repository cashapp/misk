package misk.client

import com.google.inject.Inject
import misk.ActionDeadline
import misk.ActionDeadlineProvider
import misk.exceptions.ActionException
import misk.exceptions.StatusCode
import misk.scope.ActionScoped
import okhttp3.Response
import retrofit2.Call
import java.lang.Long.max
import javax.inject.Provider

/**
 * Interceptor that prevents an outgoing request from occurring if the deadline for the current
 * action has been reached or passed.
 */
class ClientDeadlineAppInterceptor(
  private val deadlineProvider: Provider<ActionScoped<ActionDeadline>>
) : ClientApplicationInterceptor {

  class Factory @Inject constructor(
    private val deadlineProvider: Provider<ActionScoped<ActionDeadline>>
  ) : ClientApplicationInterceptor.Factory {
    override fun create(action: ClientAction) =
        ClientDeadlineAppInterceptor(deadlineProvider)
  }

  override fun interceptBeginCall(chain: BeginClientCallChain): Call<Any> {
    val deadline = deadlineProvider.get().get()

    val remaining = deadline.remaining()
    if (remaining == null) {
      return chain.proceed(chain.args)
    }
    if (remaining.isZero) {
      // TODO: Different status code?
      throw ActionException(StatusCode.SERVICE_UNAVAILABLE, "deadline exceeded; skipping call")
    }

    return chain.proceed(chain.args)
  }

  override fun intercept(chain: ClientChain) {
    chain.proceed(chain.args, chain.callback)
  }
}

/**
 * Interceptor that propagates the remaining deadline to downstream services.
 */
class ClientDeadlineNetworkInterceptor(
  private val deadlineProvider: Provider<ActionScoped<ActionDeadline>>
) : ClientNetworkInterceptor {

  class Factory @Inject constructor(
    private val deadlineProvider: Provider<ActionScoped<ActionDeadline>>
  ) : ClientNetworkInterceptor.Factory {
    override fun create(action: ClientAction): ClientNetworkInterceptor? =
        ClientDeadlineNetworkInterceptor(deadlineProvider)
  }

  override fun intercept(chain: ClientNetworkChain): Response {
    if (chain.request.headers[ActionDeadlineProvider.HTTP_HEADER] != null) {
      return chain.proceed(chain.request)
    }

    val remaining = deadlineProvider.get().get().remaining()
    if (remaining == null) {
      return chain.proceed(chain.request)
    }

    // TODO(alec): Hacky for the time being. Deadline may have passed between early check.
    // This could return a 503 here, although that feels slightly weird.
    val millisLeft = max(remaining.toMillis(), 1)
    val request = chain.request.newBuilder()
        .addHeader(ActionDeadlineProvider.HTTP_HEADER, millisLeft.toString())
        .build()
    return chain.proceed(request)
  }
}
