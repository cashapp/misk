package misk.slack.webapi.interceptors

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.slack.webapi.SlackApi
import misk.slack.webapi.SlackConfig
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation

@Singleton
class SlackClientInterceptor @Inject constructor(val config: SlackConfig) : Interceptor {
  private val bearerToken = config.bearer_token.value

  /**
   * The SlackClientInterceptor intercepts outgoing requests to Slack and chains the bearer token to the request. This
   * token is provided by the Slack API when a Slack app is originally created.
   * https://api.slack.com/web#url-encoded-bodies
   */
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val invocation = request.tag(Invocation::class.java)

    if (invocation?.method()?.declaringClass != SlackApi::class.java) {
      // Don't do anything if this isn't a Slack API call!
      return chain.proceed(request)
    }

    val authorizedRequest = chain.request().newBuilder().header("Authorization", "Bearer $bearerToken").build()

    return chain.proceed(authorizedRequest)
  }
}
