package `slack-api`

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation

@Singleton
class SlackClientInterceptor @Inject constructor(
  val config: SlackConfig,
) : Interceptor {
  private val bearerToken = config.bearer_token.value
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val invocation = request.tag(Invocation::class.java)

    if (invocation?.method()?.declaringClass != SlackApi::class.java) {
      // Don't do anything if this isn't a Slack API call!
      return chain.proceed(request)
    }

    val authorizedRequest = chain.request()
      .newBuilder()
      .header("Authorization", "Bearer $bearerToken")
      .build()

    return chain.proceed(authorizedRequest)
  }
}

