package misk.client

import com.google.inject.Inject
import misk.Deadline
import misk.DeadlineProvider
import misk.exceptions.ActionException
import misk.exceptions.StatusCode
import misk.scope.ActionScoped
import okhttp3.Response
import retrofit2.Call
import java.lang.Long.min
import javax.inject.Provider

/**
 * Interceptor that prevents an outgoing request from occurring if the deadline for the current
 * action has been reached or passed.
 */
class ClientDeadlineAppInterceptor(
  private val deadlineProvider: Provider<ActionScoped<Deadline?>>
) : ClientApplicationInterceptor {

  class Factory @Inject constructor(
    private val deadlineProvider: Provider<ActionScoped<Deadline?>>
  ) : ClientApplicationInterceptor.Factory {
    override fun create(action: ClientAction) =
        ClientDeadlineAppInterceptor(deadlineProvider)
  }

  override fun interceptBeginCall(chain: BeginClientCallChain): Call<Any> {
    val deadline = deadlineProvider.get().get()
    if (deadline == null) {
      return chain.proceed(chain.args)
    }

    val remaining = deadline.remaining().toMillis()
    if (remaining == 0L) {
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
  private val deadlineProvider: Provider<ActionScoped<Deadline?>>
) : ClientNetworkInterceptor {

  class Factory @Inject constructor(
    private val deadlineProvider: Provider<ActionScoped<Deadline?>>
  ) : ClientNetworkInterceptor.Factory {
    override fun create(action: ClientAction): ClientNetworkInterceptor? =
        ClientDeadlineNetworkInterceptor(deadlineProvider)
  }

  override fun intercept(chain: ClientNetworkChain): Response {
    if (chain.request.headers[DeadlineProvider.HTTP_HEADER] != null) {
      return chain.proceed(chain.request)
    }

    val deadline = deadlineProvider.get().get()
    if (deadline == null) {
      return chain.proceed(chain.request)
    }

    // TODO(alec): Hacky for the time being. Deadline may have passed between early check.
    // This could return a 503 here, although that feels slightly weird.
    val millisLeft = min(deadline.remaining().toMillis(), 1)
    val request = chain.request.newBuilder()
        .addHeader(DeadlineProvider.HTTP_HEADER, millisLeft.toString())
        .build()
    return chain.proceed(request)
  }
}
