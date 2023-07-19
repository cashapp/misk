package misk.slack.webapi

import misk.slack.webapi.helpers.PostMessageRequest
import misk.slack.webapi.helpers.GetUserResponse
import misk.slack.webapi.helpers.PostMessageResponse
import retrofit2.Response
import java.io.IOException
import java.io.UncheckedIOException
import javax.inject.Inject

class RealSlackClient @Inject constructor(
  private val slackApi: SlackApi,
) : SlackClient {
  override fun postMessage(request: PostMessageRequest): PostMessageResponse {
    return callSlack { slackApi.postMessage(request).execute() }
  }

  override fun postConfirmation(url: String, request: PostMessageRequest): PostMessageResponse {
    return callSlack { slackApi.postConfirmation(url, request).execute() }
  }

  override fun getUserByEmail(email: String): GetUserResponse {
    return callSlack { slackApi.getUserByEmail(email).execute() }
  }

  private fun <T> callSlack(callable: () -> Response<T>): T {
    try {
      val response = callable()
      return response.body()!!
    } catch (e: IOException) {
      throw UncheckedIOException(e)
    }
  }
}

